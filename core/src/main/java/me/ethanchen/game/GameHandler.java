package me.ethanchen.game;

import java.util.ArrayList;
import java.util.Arrays;

import me.ethanchen.game.board.Board;
import me.ethanchen.game.board.LineClearResult;
import me.ethanchen.game.board.Piece;
import me.ethanchen.game.board.SpinType;


public class GameHandler {
    private GameMode mode;
    private final int numPlayers;
    private ArrayList<Board> boards;
    /** Per-player gravity accumulators (ms). */
    private int[] gravityTickCounters;
    /** Match gravity interval in ms per row at full fall speed (ramped by scoring). */
    private int gravity;
    /**
     * Per-player fall-speed multipliers (1 = normal). The Noob's passive uses 0.5 so that
     * player's piece falls half as fast.
     */
    private float[] playerGravitySpeedMult;
    /**
     * Global fall-speed factor from abilities such as The Noob's disable/ramp
     * ({@code 0} = frozen, {@code 1} = full speed).
     */
    private float globalGravitySpeedFactor = 1f;
    private long startDelay;
    private boolean started;
    private ArrayList<LineClearResult> pendingLockResults = new ArrayList<>();

    // Global counters shared across all game modes
    private int b2b = 0;
    private int combo = 0;
    private int previousComboPlayerId = -1;

    public GameHandler(int numPlayers) {
        this.numPlayers = numPlayers;
        boards = new ArrayList<Board>();
        started = false;
        gravityTickCounters = new int[numPlayers];
        playerGravitySpeedMult = new float[numPlayers];
        Arrays.fill(playerGravitySpeedMult, 1f);
    }

    public void init(GameMode m, long startGameTimer) {
        mode = m;
        startDelay = startGameTimer;
        if (mode == GameMode.NONE) return;
        GameModeRules rules = mode.rules();
        gravity = rules.initialGravityMs();
        Arrays.fill(gravityTickCounters, 0);
        Arrays.fill(playerGravitySpeedMult, 1f);
        globalGravitySpeedFactor = 1f;
        Board board = new Board(rules.boardPreset(numPlayers));
        rules.prepareBoard(board);
        boards.add(board);
    }

    public void startGame() {
        started = true;
        for (Board b : boards) {
            b.spawnInitialPieces();
        }
    }

    public void update(int deltaTime) {
        if (startDelay > 0) {
            startDelay -= deltaTime;
        } else if (!started) {
            startGame();
        }
        if (!started) return;
        if (mode != GameMode.NONE) {
            doGravity(deltaTime);
            doLockTimers(deltaTime);
            doFallingBlocks(deltaTime);
            doMovementTimers(deltaTime);
            updateJustSpawnedFlags();
        }
    }

    private void updateJustSpawnedFlags() {
        for (Board b : boards)
            b.updateJustSpawnedFlags();
    }

    private void doGravity(int deltaTime) {
        if (!started) return;
        for (int playerId = 0; playerId < numPlayers; playerId++) {
            float speed = fallSpeedFor(playerId);
            if (speed <= 0f) {
                // Frozen: do not accumulate so thawing does not dump buffered ticks.
                continue;
            }
            int interval = Math.max(1, Math.round(gravity / speed));
            gravityTickCounters[playerId] += deltaTime;
            while (gravityTickCounters[playerId] >= interval) {
                doGravityTick(playerId);
                gravityTickCounters[playerId] -= interval;
            }
        }
    }

    private float fallSpeedFor(int playerId) {
        float personal = (playerId >= 0 && playerId < playerGravitySpeedMult.length)
                ? playerGravitySpeedMult[playerId] : 1f;
        return personal * globalGravitySpeedFactor;
    }

    public void doGravityTick() {
        for (int i = 0; i < numPlayers; i++) {
            doGravityTick(i);
        }
    }

    public void doGravityTick(int playerId) {
        for (Board b : boards) {
            b.doGravityTick(playerId);
        }
    }

    private void doLockTimers(int deltaTime) {
        if (!started) return;
        for (Board b : boards)
            pendingLockResults.addAll(b.updateLockTimers(deltaTime));
    }

    private void doFallingBlocks(int deltaTime) {
        if (!started) return;
        for (Board b : boards)
            pendingLockResults.addAll(b.updateFallingBlocks(deltaTime));
    }

    private void doMovementTimers(int deltaTime) {
        if (!started) return;
        for (Board b : boards)
            b.updateMovementTimers(deltaTime);
    }

    /**
     * Returns all auto-lock results accumulated since the last call and clears the list.
     */
    public ArrayList<LineClearResult> getAndClearPendingLockResults() {
        if (pendingLockResults.isEmpty()) return new ArrayList<>();
        ArrayList<LineClearResult> copy = new ArrayList<>(pendingLockResults);
        pendingLockResults.clear();
        return copy;
    }

    /**
     * Returns true when a line clear is eligible to increment (or extend) back-to-back:
     * any spin type, a 4-line clear, an I3 3-line clear, or an all clear.
     */
    public static boolean isB2BEligible(LineClearResult r) {
        return r.spinType != SpinType.NONE
                || r.numClearedRows() == 4
                || (r.pieceType == Piece.I3 && r.numClearedRows() == 3)
                || r.allClear;
    }

    /**
     * Updates the global b2b, combo and previousComboPlayerId counters based on the
     * result of a hard drop or falling-column landing.  Must be called AFTER scoring so
     * that pre-clear values can be read during score calculation.
     * <p>
     * Rules:
     * <ul>
     *   <li>Any piece placement with {@code lines == 0}: resets combo to 0.</li>
     *   <li>Any piece placement with {@code lines > 0}: increments combo, updates
     *       previousComboPlayerId; increments b2b if eligible, resets it otherwise.</li>
     * </ul>
     */
    public void applyClearToCounters(LineClearResult r) {
        if (!r.placed) return;
        if (r.numClearedRows() == 0) {
            combo = 0;
        } else {
            combo++;
            previousComboPlayerId = r.playerId;
            if (isB2BEligible(r)) {
                b2b++;
            } else {
                b2b = 0;
            }
        }
    }

    // Getters/Setters

    public int getNumPlayers() {
        return numPlayers;
    }

    public ArrayList<Board> getBoards() {
        return boards;
    }

    public GameMode getMode() {
        return mode;
    }

    public boolean isStarted() {
        return started;
    }

    /** Base gravity interval (ms/row) before per-player / ability speed modifiers. */
    public int getGravity() {
        return gravity;
    }

    /**
     * Effective gravity interval for {@code playerId} after personal and global speed factors.
     * Returns a very large interval when fall speed is zero (gravity disabled).
     */
    public int getEffectiveGravityMs(int playerId) {
        float speed = fallSpeedFor(playerId);
        if (speed <= 0f) return Integer.MAX_VALUE / 4;
        return Math.max(1, Math.round(gravity / speed));
    }

    public int getB2b() { return b2b; }
    public int getCombo() { return combo; }
    public int getPreviousComboPlayerId() { return previousComboPlayerId; }

    /** Resets every player's gravity accumulator. */
    public void resetGravityTimer() {
        Arrays.fill(gravityTickCounters, 0);
    }

    /** Resets one player's gravity accumulator (e.g. after a soft drop). */
    public void resetGravityTimer(int playerId) {
        if (playerId < 0 || playerId >= gravityTickCounters.length) return;
        gravityTickCounters[playerId] = 0;
    }

    public void setGravity(int g) {
        gravity = g;
    }

    /** Returns player 0's accumulator; prefer {@link #getGravityTickCounter(int)}. */
    public int getGravityTickCounter() {
        return getGravityTickCounter(0);
    }

    public int getGravityTickCounter(int playerId) {
        if (playerId < 0 || playerId >= gravityTickCounters.length) return 0;
        return gravityTickCounters[playerId];
    }

    /** Defensive copy of all per-player gravity accumulators for network sync. */
    public int[] copyGravityTickCounters() {
        return Arrays.copyOf(gravityTickCounters, gravityTickCounters.length);
    }

    public void setGravityTickCounter(int c) {
        setGravityTickCounter(0, c);
    }

    public void setGravityTickCounter(int playerId, int c) {
        if (playerId < 0 || playerId >= gravityTickCounters.length) return;
        gravityTickCounters[playerId] = c;
    }

    public void setPlayerGravitySpeedMult(int playerId, float multiplier) {
        if (playerId < 0 || playerId >= playerGravitySpeedMult.length) return;
        playerGravitySpeedMult[playerId] = Math.max(0f, multiplier);
    }

    public float getPlayerGravitySpeedMult(int playerId) {
        if (playerId < 0 || playerId >= playerGravitySpeedMult.length) return 1f;
        return playerGravitySpeedMult[playerId];
    }

    public void setGlobalGravitySpeedFactor(float factor) {
        if (factor < 0f) factor = 0f;
        globalGravitySpeedFactor = factor;
    }

    public float getGlobalGravitySpeedFactor() {
        return globalGravitySpeedFactor;
    }
}
