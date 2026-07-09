package me.ethanchen.lwjgl3.menuscreens;

import java.util.ArrayList;
import java.util.Iterator;

import me.ethanchen.game.GameHandler;
import me.ethanchen.game.board.Board;
import me.ethanchen.game.board.MoveType;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.music.AudioManager;
import me.ethanchen.network.packets.c2s.MoveListRequest;

/**
 * Manages the client-side pending-move queue and client-side prediction replay. Extracted from
 * {@link GameScreen} to separate network-reliability plumbing from input handling and rendering.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Call {@link #queueMove} when a move should be sent to the server (also applies
 *       immediately to the local board for non-authority moves).
 *   <li>Call {@link #sendIfNeeded} each tick to re-send unacknowledged moves.
 *   <li>Call {@link #ackMovesUpTo} on every {@code LightGameStateBroadcast} to prune
 *       acknowledged moves and replay the remainder on top of the server state.
 * </ol>
 */
class ClientMovePredictor {

    private static final long MOVE_RESEND_INTERVAL_MS = 100;
    static final int MAX_PENDING_MOVES = 100;

    private final ArrayList<PendingMove> pendingMoves = new ArrayList<>();
    private int nextMoveId = 0;
    private long lastMoveSendMs = 0;
    private final ClientApp app;
    private final int playerId;

    ClientMovePredictor(ClientApp app, int playerId) {
        this.app = app;
        this.playerId = playerId;
    }

    boolean hasTooManyPending() { return pendingMoves.size() > MAX_PENDING_MOVES; }
    boolean hasAny() { return !pendingMoves.isEmpty(); }

    // -------------------------------------------------------------------------
    // Queueing
    // -------------------------------------------------------------------------

    /**
     * Adds a move to the pending queue, applies it optimistically to the local board (for
     * movement/rotation; hard-drop and hold are server-authoritative), and sends it immediately.
     *
     * @param type          the move to perform
     * @param board         the local board (used for optimistic application + audio feedback)
     * @param game          the local game handler (used for gravity reset on soft-drop)
     * @param holdAvailable whether hold is currently available (gates hold moves)
     */
    void queueMove(MoveType type, Board board, GameHandler game, boolean holdAvailable) {
        if (type == MoveType.HOLD && !holdAvailable) {
            AudioManager.getInstance().playHoldSound(true, false);
            return;
        }
        pendingMoves.add(new PendingMove(nextMoveId++, type));
        // Hard drop and hold are server-authoritative; skip local application to avoid desyncing
        // the piece queue during prediction replay.
        if (type != MoveType.HARD_DROP && type != MoveType.HOLD) {
            boolean moved = board.applyMove(playerId, type);
            if (moved) {
                if (type == MoveType.LEFT || type == MoveType.RIGHT || type == MoveType.SOFT_DROP) {
                    AudioManager.getInstance().playMoveSound();
                } else if (type == MoveType.ROTATE_CW || type == MoveType.ROTATE_CCW
                        || type == MoveType.ROTATE_180) {
                    AudioManager.getInstance().playRotateSound();
                }
            }
        }
        send();
    }

    // -------------------------------------------------------------------------
    // Periodic resend
    // -------------------------------------------------------------------------

    /** Resends all pending moves if the resend interval has elapsed. */
    void sendIfNeeded() {
        if (!pendingMoves.isEmpty()
                && System.currentTimeMillis() - lastMoveSendMs >= MOVE_RESEND_INTERVAL_MS) {
            send();
        }
    }

    // -------------------------------------------------------------------------
    // Ack + prediction replay (called on LightGameStateBroadcast)
    // -------------------------------------------------------------------------

    /**
     * Removes moves acknowledged by the server (id <= {@code ackMoveId}), then replays all
     * remaining pending moves on top of the server's board state. Hard-drop and hold are
     * excluded from replay since the server result arrives in a subsequent broadcast.
     *
     * @param ackMoveId the highest move id the server has processed
     * @param board     the local board, already updated to the server state
     */
    void ackMovesUpTo(int ackMoveId, Board board) {
        Iterator<PendingMove> it = pendingMoves.iterator();
        while (it.hasNext()) {
            if (it.next().id <= ackMoveId) it.remove();
        }
        for (PendingMove pm : pendingMoves) {
            if (pm.type != MoveType.HARD_DROP && pm.type != MoveType.HOLD) {
                board.applyMove(playerId, pm.type);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void send() {
        if (pendingMoves.isEmpty()) return;
        MoveListRequest req = new MoveListRequest();
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
