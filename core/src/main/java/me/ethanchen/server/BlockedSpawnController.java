package me.ethanchen.server;

import me.ethanchen.game.GameConstants;
import me.ethanchen.game.GameHandler;
import me.ethanchen.game.board.Board;
import me.ethanchen.game.board.Piece;

/**
 * Manages per-player piece-cycling while blocked at spawn, hold-while-blocked mechanics,
 * and the explode countdown that ends the game once all players are simultaneously blocked.
 * Extracted from {@link ServerGame}.
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

    /** Re-initialises all cycling state for a new game. */
    void reset(int players) {
        timeBetweenNextPiece = new float[players];
        cycleTimer           = new float[players];
        lastCycleSwitchMs    = new long[players];
        previousCyclePieceId = new byte[players];
        wasBlocked           = new boolean[players];
        java.util.Arrays.fill(timeBetweenNextPiece, GameConstants.CYCLE_START);
        explodeCountdown = -1f;
        lastHoldUsedMs   = 0;
    }

    float getExplodeProgress() { return explodeCountdown; }

    // -------------------------------------------------------------------------
    // Per-tick update
    // -------------------------------------------------------------------------

    /**
     * Advances all per-player blocked cycling timers and the global explode countdown.
     *
     * @param dtSec       elapsed seconds since last tick
     * @param players     number of active players
     * @param game        live game handler
     * @param onGameEnd   called with {@code (win=false)} when the explode countdown expires
     */
    void update(float dtSec, int players, GameHandler game, Runnable onGameEnd) {
        if (game == null || game.getBoards().isEmpty()) return;
        Board board = game.getBoards().get(0);
        if (board.getActivePieces().isEmpty()) return;

        long now = System.currentTimeMillis();

        for (int i = 0; i < players; i++) {
            if (i >= board.getActivePieces().size()) continue;
            Piece piece = board.getActivePieces().get(i);
            boolean blocked = piece.isBlockedFromSpawning;

            if (blocked && !wasBlocked[i]) {
                timeBetweenNextPiece[i] = GameConstants.CYCLE_START;
                cycleTimer[i] = 0f;
            }
            if (!blocked && wasBlocked[i]) {
                cycleTimer[i] = 0f;
            }
            wasBlocked[i] = blocked;

            if (!blocked) continue;

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
                    wasBlocked[i] = false;
                    cycleTimer[i] = 0f;
                    if (explodeCountdown >= 0f) {
                        explodeCountdown = -1f; // near-death save
                    }
                    break;
                }
            }
        }

        boolean allBlocked = players > 0;
        for (int i = 0; i < players; i++) {
            if (i >= board.getActivePieces().size()) { allBlocked = false; break; }
            Piece p = board.getActivePieces().get(i);
            if (!p.isBlockedFromSpawning) {
                allBlocked = false;
                break;
            }
        }

        if (allBlocked) {
            if (explodeCountdown < 0f) explodeCountdown = 0f;
            explodeCountdown += dtSec;
            if (explodeCountdown >= GameConstants.EXPLODE_DURATION) {
                onGameEnd.run();
            }
        } else if (explodeCountdown >= 0f) {
            explodeCountdown = -1f;
        }
    }

    // -------------------------------------------------------------------------
    // Hold-while-blocked
    // -------------------------------------------------------------------------

    /**
     * Returns true when player {@code i}'s blocked piece may be held
     * (cycling has reached minimum interval and explode is not active).
     */
    boolean canHoldWhileBlocked(int i) {
        if (timeBetweenNextPiece == null || i < 0 || i >= timeBetweenNextPiece.length) return false;
        return timeBetweenNextPiece[i] <= GameConstants.CYCLE_MIN && explodeCountdown < 0f;
    }

    /**
     * Returns true if player {@code playerId}'s piece is blocked and may be held right now.
     */
    boolean computeHoldAvailable(int playerId, Board board) {
        if (board.getActivePieces().size() > playerId
                && board.getActivePieces().get(playerId).isBlockedFromSpawning) {
            if (explodeCountdown >= 0f) return false;
            return canHoldWhileBlocked(playerId);
        }
        long now = System.currentTimeMillis();
        boolean globalLock = lastHoldUsedMs > 0 && (now - lastHoldUsedMs) < GameConstants.HOLD_GLOBAL_LOCK_MS;
        return !board.isPlayerHoldUsed(playerId) && !globalLock;
    }

    /**
     * Returns true when the controlling player's piece is blocked and can be held (triggers
     * the hold-glow indicator on the client).
     */
    boolean computeOwnPieceHoldGlow(int playerId, Board board) {
        if (board.getActivePieces().size() <= playerId) return false;
        Piece p = board.getActivePieces().get(playerId);
        return p.isBlockedFromSpawning && canHoldWhileBlocked(playerId);
    }

    /**
     * Applies a hold action for a blocked player, with coyote-time support. Queues the hold
     * sound via {@code effects}.
     */
    void applyBlockedHold(int playerId, Board board, PlacementEffects effects) {
        if (!canHoldWhileBlocked(playerId)) return;
        if (board.getActivePieces().size() <= playerId) return;

        long now = System.currentTimeMillis();
        byte currentType = board.getActivePieces().get(playerId).type;
        byte effectiveType = (lastCycleSwitchMs[playerId] > 0
                && (now - lastCycleSwitchMs[playerId]) <= GameConstants.COYOTE_MS)
                ? previousCyclePieceId[playerId]
                : currentType;

        byte oldHeld = board.getHeldPieceType();
        board.setHeldPieceType(effectiveType);

        if (oldHeld == 0) {
            board.spawnNextPiece(playerId);
        } else {
            board.spawnHeldPiece(playerId, oldHeld);
        }

        timeBetweenNextPiece[playerId] = GameConstants.CYCLE_START;
        cycleTimer[playerId] = 0f;
        lastHoldUsedMs = System.currentTimeMillis();
        effects.addHoldSound((byte) playerId, true);
    }

    void setLastHoldUsedMs(long ms) { lastHoldUsedMs = ms; }
    long getLastHoldUsedMs()        { return lastHoldUsedMs; }

    // -------------------------------------------------------------------------

    private float effectiveInterval(int i) {
        if (explodeCountdown >= 0f) {
            float frac = Math.min(explodeCountdown, 1f);
            return GameConstants.CYCLE_MIN + (GameConstants.EXPLODE_MIN_INTERVAL - GameConstants.CYCLE_MIN) * frac;
        }
        return timeBetweenNextPiece[i];
    }
}
