package me.ethanchen.server;

import me.ethanchen.game.GameConstants;
import me.ethanchen.game.board.Board;
import me.ethanchen.game.board.Piece;

/**
 * Manages one board's per-seat piece-cycling while blocked at spawn, hold-while-blocked
 * mechanics, and the explode countdown that eliminates that board once every seat on it is
 * simultaneously blocked. Extracted from {@link ServerGame}; one instance exists per board so the
 * hold lock and explode countdown never leak between boards.
 * <p>
 * Once every seat is spawn-blocked, the board is locked into the explode animation: pieces keep
 * cycling even if a later bag piece would fit, and hold is disabled for the rest of the countdown.
 * <p>
 * All indices taken by this class ({@code seat}) are board-local seat indices, matching the
 * given {@link Board}'s own {@code activePieces} ordering — not global session slots.
 */
class BlockedSpawnController {

    private float[]  timeBetweenNextPiece;
    private float[]  cycleTimer;
    private long[]   lastCycleSwitchMs;
    private byte[]   previousCyclePieceId;
    private boolean[] wasBlocked;
    private float    explodeCountdown = -1f;
    private long     lastHoldUsedMs   = 0;

    BlockedSpawnController() {}

    /** Re-initialises all cycling state for a new game. {@code seatCount} is this board's own seat count. */
    void reset(int seatCount) {
        timeBetweenNextPiece = new float[seatCount];
        cycleTimer           = new float[seatCount];
        lastCycleSwitchMs    = new long[seatCount];
        previousCyclePieceId = new byte[seatCount];
        wasBlocked           = new boolean[seatCount];
        java.util.Arrays.fill(timeBetweenNextPiece, GameConstants.CYCLE_START);
        explodeCountdown = -1f;
        lastHoldUsedMs   = 0;
    }

    float getExplodeProgress() { return explodeCountdown; }

    // -------------------------------------------------------------------------
    // Per-tick update
    // -------------------------------------------------------------------------

    /**
     * Advances this board's per-seat blocked cycling timers and its own explode countdown.
     *
     * @param dtSec       elapsed seconds since last tick
     * @param board       this controller's board
     * @param onBoardLost called with once this board's explode countdown expires (that board is eliminated)
     */
    void update(float dtSec, Board board, Runnable onBoardLost) {
        if (board == null || board.getActivePieces().isEmpty()) return;
        int seats = timeBetweenNextPiece.length;

        long now = System.currentTimeMillis();

        // Latch before cycling so a piece that would fit cannot cancel the animation.
        if (explodeCountdown < 0f && allSeatsBlocked(board)) {
            explodeCountdown = 0f;
        }
        boolean locked = explodeCountdown >= 0f;

        for (int i = 0; i < seats; i++) {
            if (i >= board.getActivePieces().size()) continue;
            Piece piece = board.getActivePieces().get(i);
            boolean blockedNow = piece.isBlockedFromSpawning;
            if (locked && !blockedNow) {
                piece.isBlockedFromSpawning = true;
                blockedNow = true;
            }

            if (blockedNow && !wasBlocked[i]) {
                timeBetweenNextPiece[i] = GameConstants.CYCLE_START;
                cycleTimer[i] = 0f;
            }
            if (!blockedNow && wasBlocked[i]) {
                cycleTimer[i] = 0f;
            }
            wasBlocked[i] = blockedNow;

            if (!blockedNow) continue;

            cycleTimer[i] += dtSec;
            float interval = effectiveInterval(i);
            while (cycleTimer[i] >= interval) {
                cycleTimer[i] -= interval;
                previousCyclePieceId[i] = board.getActivePieces().get(i).type;
                lastCycleSwitchMs[i] = now;
                board.spawnNextPiece(i);
                timeBetweenNextPiece[i] = Math.max(GameConstants.CYCLE_MIN, timeBetweenNextPiece[i] * GameConstants.CYCLE_MULT);
                interval = effectiveInterval(i);
                Piece newPiece = board.getActivePieces().get(i);
                if (!newPiece.isBlockedFromSpawning) {
                    if (locked) {
                        newPiece.isBlockedFromSpawning = true;
                    } else {
                        wasBlocked[i] = false;
                        cycleTimer[i] = 0f;
                        break;
                    }
                }
            }
        }

        if (locked) {
            explodeCountdown += dtSec;
            if (explodeCountdown >= GameConstants.EXPLODE_DURATION) {
                onBoardLost.run();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Hold-while-blocked
    // -------------------------------------------------------------------------

    /**
     * Returns true when seat {@code seat}'s blocked piece may be held
     * (cycling has reached minimum interval, not every seat is blocked, and explode is inactive).
     */
    boolean canHoldWhileBlocked(int seat, Board board) {
        if (timeBetweenNextPiece == null || seat < 0 || seat >= timeBetweenNextPiece.length) return false;
        if (explodeCountdown >= 0f || allSeatsBlocked(board)) return false;
        return timeBetweenNextPiece[seat] <= GameConstants.CYCLE_MIN;
    }

    /**
     * Returns true if seat {@code seat}'s piece is blocked and may be held right now.
     */
    boolean computeHoldAvailable(int seat, Board board) {
        if (board.getActivePieces().size() > seat
                && board.getActivePieces().get(seat).isBlockedFromSpawning) {
            return canHoldWhileBlocked(seat, board);
        }
        long now = System.currentTimeMillis();
        boolean boardLock = lastHoldUsedMs > 0 && (now - lastHoldUsedMs) < GameConstants.HOLD_GLOBAL_LOCK_MS;
        return !board.isPlayerHoldUsed(seat) && !boardLock;
    }

    /**
     * Returns true when the controlling seat's piece is blocked and can be held (triggers
     * the hold-glow indicator on the client).
     */
    boolean computeOwnPieceHoldGlow(int seat, Board board) {
        if (board.getActivePieces().size() <= seat) return false;
        Piece p = board.getActivePieces().get(seat);
        return p.isBlockedFromSpawning && canHoldWhileBlocked(seat, board);
    }

    /**
     * Applies a hold action for a blocked seat, with coyote-time support. Queues the hold
     * sound (addressed to {@code globalPlayerId}, for network identification) via {@code effects}.
     */
    void applyBlockedHold(int seat, int globalPlayerId, Board board, PlacementEffects effects) {
        if (!canHoldWhileBlocked(seat, board)) return;
        if (board.getActivePieces().size() <= seat) return;

        long now = System.currentTimeMillis();
        byte currentType = board.getActivePieces().get(seat).type;
        byte effectiveType = (lastCycleSwitchMs[seat] > 0
                && (now - lastCycleSwitchMs[seat]) <= GameConstants.COYOTE_MS)
                ? previousCyclePieceId[seat]
                : currentType;

        byte oldHeld = board.getHeldPieceType();
        board.setHeldPieceType(effectiveType);

        if (oldHeld == 0) {
            board.spawnNextPiece(seat);
        } else {
            board.spawnHeldPiece(seat, oldHeld);
        }

        timeBetweenNextPiece[seat] = GameConstants.CYCLE_START;
        cycleTimer[seat] = 0f;
        lastHoldUsedMs = System.currentTimeMillis();
        effects.addHoldSound((byte) globalPlayerId, true);
    }

    void setLastHoldUsedMs(long ms) { lastHoldUsedMs = ms; }
    long getLastHoldUsedMs()        { return lastHoldUsedMs; }

    // -------------------------------------------------------------------------

    private boolean allSeatsBlocked(Board board) {
        int seats = timeBetweenNextPiece.length;
        if (seats <= 0 || board.getActivePieces().size() < seats) return false;
        for (int i = 0; i < seats; i++) {
            if (!board.getActivePieces().get(i).isBlockedFromSpawning) return false;
        }
        return true;
    }

    private float effectiveInterval(int i) {
        if (explodeCountdown >= 0f) {
            float frac = Math.min(explodeCountdown, 1f);
            return GameConstants.CYCLE_MIN + (GameConstants.EXPLODE_MIN_INTERVAL - GameConstants.CYCLE_MIN) * frac;
        }
        return timeBetweenNextPiece[i];
    }
}
