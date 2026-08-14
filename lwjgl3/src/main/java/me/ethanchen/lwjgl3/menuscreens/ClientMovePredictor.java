package me.ethanchen.lwjgl3.menuscreens;

import java.util.ArrayList;
import java.util.Iterator;

import me.ethanchen.game.GameHandler;
import me.ethanchen.game.board.Board;
import me.ethanchen.game.board.MoveType;
import me.ethanchen.game.board.SpinType;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.music.AudioManager;
import me.ethanchen.network.packets.c2s.MoveListRequest;

/**
 * Manages the client-side pending-move queue and client-side prediction replay for a single
 * local player.
 */
class ClientMovePredictor {

    private static final long MOVE_RESEND_INTERVAL_MS = 100;
    static final int MAX_PENDING_MOVES = 100;

    private final ArrayList<PendingMove> pendingMoves = new ArrayList<>();
    private int nextMoveId = 0;
    private long lastMoveSendMs = 0;
    private final ClientApp app;
    private final int seat;
    private final byte localIndex;

    ClientMovePredictor(ClientApp app, int seat, int localIndex) {
        this.app = app;
        this.seat = seat;
        this.localIndex = (byte) localIndex;
    }

    byte getLocalIndex() { return localIndex; }

    boolean hasTooManyPending() { return pendingMoves.size() > MAX_PENDING_MOVES; }
    boolean hasAny() { return !pendingMoves.isEmpty(); }

    void queueMove(MoveType type, Board board, GameHandler game, boolean holdAvailable) {
        if (type == MoveType.HOLD && !holdAvailable) {
            AudioManager.getInstance().playHoldSound(true, false);
            return;
        }
        pendingMoves.add(new PendingMove(nextMoveId++, type));
        // Hard drop and hold are server-authoritative; skip local application to avoid desyncing
        // the piece queue during prediction replay.
        if (type != MoveType.HARD_DROP && type != MoveType.HOLD) {
            boolean moved = board.applyMove(seat, type);
            if (moved) {
                if (type == MoveType.LEFT || type == MoveType.RIGHT || type == MoveType.SOFT_DROP) {
                    AudioManager.getInstance().playMoveSound();
                } else if (type == MoveType.ROTATE_CW || type == MoveType.ROTATE_CCW
                        || type == MoveType.ROTATE_180) {
                    if (board.detectSpinType(seat) != SpinType.NONE) {
                        AudioManager.getInstance().playSpinTurnSound();
                    } else {
                        AudioManager.getInstance().playRotateSound();
                    }
                }
            }
        }
        send();
    }

    /** Resends all pending moves if the resend interval has elapsed. */
    void sendIfNeeded() {
        if (!pendingMoves.isEmpty()
                && System.currentTimeMillis() - lastMoveSendMs >= MOVE_RESEND_INTERVAL_MS) {
            send();
        }
    }

    void ackMovesUpTo(int ackMoveId, Board board) {
        Iterator<PendingMove> it = pendingMoves.iterator();
        while (it.hasNext()) {
            if (it.next().id <= ackMoveId) it.remove();
        }
        for (PendingMove pm : pendingMoves) {
            if (pm.type != MoveType.HARD_DROP && pm.type != MoveType.HOLD) {
                board.applyMove(seat, pm.type);
            }
        }
    }

    private void send() {
        if (pendingMoves.isEmpty()) return;
        MoveListRequest req = new MoveListRequest();
        req.localIndex = localIndex;
        req.ids = new int[pendingMoves.size()];
        req.types = new byte[pendingMoves.size()];
        for (int i = 0; i < pendingMoves.size(); i++) {
            req.ids[i] = pendingMoves.get(i).id;
            req.types[i] = (byte) pendingMoves.get(i).type.ordinal();
        }
        app.sendUDP(req);
        lastMoveSendMs = System.currentTimeMillis();
    }

    static final class PendingMove {
        final int id;
        final MoveType type;
        PendingMove(int id, MoveType type) { this.id = id; this.type = type; }
    }
}
