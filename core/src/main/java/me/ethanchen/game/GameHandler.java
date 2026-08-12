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
    /**
     * Maps each global session slot to the index of the board it is seated on. Currently every
     * slot maps to board 0 (the only board created); this exists so board-specific logic can be
     * written correctly ahead of true multi-board support.
     */
    private int[] slotBoard;
    /** Maps each global session slot to its board-local seat index (see {@link Board#globalSlotForSeat}). */
    private int[] slotSeat;
    /** Per-player gravity accumulators (ms). */
    private int[] gravityTickCounters;
    /**
     * Per-player fall-speed multipliers (1 = normal). The Noob's passive uses 0.5 so that
     * player's piece falls half as fast.
     */
    private float[] playerGravitySpeedMult;
    private long startDelay;
    private boolean started;
    private ArrayList<LineClearResult> pendingLockResults = new ArrayList<>();

    /** Per-board combo/b2b/gravity/gravity-speed state, indexed the same as {@link #boards}. */
    private ArrayList<BoardState> boardStates = new ArrayList<>();

    public GameHandler(int numPlayers) {
        this.numPlayers = numPlayers;
        boards = new ArrayList<Board>();
        started = false;
        gravityTickCounters = new int[numPlayers];
        playerGravitySpeedMult = new float[numPlayers];
        Arrays.fill(playerGravitySpeedMult, 1f);
    }

    public void init(GameMode m, long startGameTimer) {
        init(m, m == GameMode.NONE ? null : m.rules(), startGameTimer);
    }

    /**
     * Like {@link #init(GameMode, long)}, but uses an explicitly-supplied {@code rules} instance
     * instead of {@code m.rules()}. Needed for modes whose rules carry session-specific state
     * that can't live on {@link GameMode}'s singleton (e.g. PvE's selected level/difficulty —
     * see {@code PveRules}), so the caller builds a fresh rules instance per session.
     */
    public void init(GameMode m, GameModeRules rules, long startGameTimer) {
        mode = m;
        startDelay = startGameTimer;
        if (mode == GameMode.NONE || rules == null) return;
        Arrays.fill(gravityTickCounters, 0);
        Arrays.fill(playerGravitySpeedMult, 1f);

        me.ethanchen.game.board.BoardPreset[] layout = rules.boardLayout(numPlayers);
        for (me.ethanchen.game.board.BoardPreset preset : layout) {
            Board board = new Board(preset);
            rules.prepareBoard(board);
            boards.add(board);
            BoardState state = new BoardState();
            state.reset(rules.initialGravityMs());
            boardStates.add(state);
        }

        // Resolve each global slot to a board (per the mode's rules) and a board-local seat
        // index (the running count of slots already assigned to that board, in slot order).
        int[] requestedSlotBoard = rules.slotToBoard(numPlayers);
        slotBoard = new int[numPlayers];
        slotSeat = new int[numPlayers];
        int[] seatCounters = new int[boards.size()];
        for (int i = 0; i < numPlayers; i++) {
            int b = (requestedSlotBoard != null && i < requestedSlotBoard.length) ? requestedSlotBoard[i] : 0;
            if (b < 0 || b >= boards.size()) b = 0;
            slotBoard[i] = b;
            slotSeat[i] = seatCounters[b]++;
        }

        int[][] seatSlotsPerBoard = new int[boards.size()][];
        for (int b = 0; b < boards.size(); b++) {
            seatSlotsPerBoard[b] = new int[seatCounters[b]];
        }
        for (int i = 0; i < numPlayers; i++) {
            seatSlotsPerBoard[slotBoard[i]][slotSeat[i]] = i;
        }
        for (int b = 0; b < boards.size(); b++) {
            boards.get(b).setBoardIndex(b);
            boards.get(b).setSeatSlots(seatSlotsPerBoard[b]);
        }
    }

    /** Resolves the board that global slot {@code playerId} is seated on, or {@code null} if unseated. */
    public Board boardFor(int playerId) {
        int bi = boardIndexOf(playerId);
        return (bi >= 0 && bi < boards.size()) ? boards.get(bi) : null;
    }

    /** Index of the board that global slot {@code playerId} is seated on, or {@code -1} if unseated. */
    public int boardIndexOf(int playerId) {
        if (slotBoard == null || playerId < 0 || playerId >= slotBoard.length) return -1;
        return slotBoard[playerId];
    }

    /** Board-local seat index for global slot {@code playerId} (defaults to identity if unmapped). */
    public int seatOf(int playerId) {
        if (slotSeat == null || playerId < 0 || playerId >= slotSeat.length) return playerId;
        return slotSeat[playerId];
    }

    /** Every global slot currently seated on board {@code boardIndex}, in seat order. */
    public int[] slotsOnBoard(int boardIndex) {
        if (slotBoard == null) return new int[0];
        int count = 0;
        for (int b : slotBoard) if (b == boardIndex) count++;
        int[] result = new int[count];
        int w = 0;
        for (int i = 0; i < slotBoard.length; i++) {
            if (slotBoard[i] == boardIndex) result[w++] = i;
        }
        return result;
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
            int boardGravity = boardStateOrDefault(boardIndexOf(playerId)).getGravity();
            int interval = Math.max(1, Math.round(boardGravity / speed));
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
        float boardFactor = boardStateOrDefault(boardIndexOf(playerId)).getGravitySpeedFactor();
        return personal * boardFactor;
    }

    /** Resolves the {@link BoardState} for {@code boardIndex}, or a fresh default instance if out of range. */
    private BoardState boardStateOrDefault(int boardIndex) {
        if (boardIndex >= 0 && boardIndex < boardStates.size()) return boardStates.get(boardIndex);
        return new BoardState();
    }

    public void doGravityTick() {
        for (int i = 0; i < numPlayers; i++) {
            doGravityTick(i);
        }
    }

    public void doGravityTick(int playerId) {
        Board b = boardFor(playerId);
        if (b != null) b.doGravityTick(seatOf(playerId));
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
     * Updates the combo, b2b and previousComboPlayerId counters of the board the result occurred
     * on (never a different board) based on the result of a hard drop or falling-column landing.
     * Must be called AFTER scoring so that pre-clear values can be read during score calculation.
     * <p>
     * Rules:
     * <ul>
     *   <li>Any piece placement with {@code lines == 0}: resets combo to 0.</li>
     *   <li>Any piece placement with {@code lines > 0}: increments combo, updates
     *       previousComboPlayerId; increments b2b if eligible, resets it otherwise.</li>
     * </ul>
     */
    public void applyClearToCounters(LineClearResult r) {
        boardStateOrDefault(r.boardIndex).applyClearToCounters(r);
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

    /** Base gravity interval (ms/row) before per-player / ability speed modifiers, for board 0. */
    public int getGravity() {
        return getGravity(0);
    }

    /** Base gravity interval (ms/row) for {@code boardIndex} before per-player / ability speed modifiers. */
    public int getGravity(int boardIndex) {
        return boardStateOrDefault(boardIndex).getGravity();
    }

    /**
     * Effective gravity interval for {@code playerId} after personal and board speed factors.
     * Returns a very large interval when fall speed is zero (gravity disabled).
     */
    public int getEffectiveGravityMs(int playerId) {
        float speed = fallSpeedFor(playerId);
        if (speed <= 0f) return Integer.MAX_VALUE / 4;
        int boardGravity = boardStateOrDefault(boardIndexOf(playerId)).getGravity();
        return Math.max(1, Math.round(boardGravity / speed));
    }

    /** Board 0's combo counter. Prefer {@link #getB2b(int)} once multiple boards exist. */
    public int getB2b() { return getB2b(0); }
    /** Board 0's combo counter. Prefer {@link #getCombo(int)} once multiple boards exist. */
    public int getCombo() { return getCombo(0); }
    /** Board 0's previous-combo player. Prefer {@link #getPreviousComboPlayerId(int)}. */
    public int getPreviousComboPlayerId() { return getPreviousComboPlayerId(0); }

    public int getB2b(int boardIndex) { return boardStateOrDefault(boardIndex).getB2b(); }
    public int getCombo(int boardIndex) { return boardStateOrDefault(boardIndex).getCombo(); }
    public int getPreviousComboPlayerId(int boardIndex) { return boardStateOrDefault(boardIndex).getPreviousComboPlayerId(); }

    /** Resets every player's gravity accumulator. */
    public void resetGravityTimer() {
        Arrays.fill(gravityTickCounters, 0);
    }

    /** Resets one player's gravity accumulator (e.g. after a soft drop). */
    public void resetGravityTimer(int playerId) {
        if (playerId < 0 || playerId >= gravityTickCounters.length) return;
        gravityTickCounters[playerId] = 0;
    }

    /** Sets board 0's gravity. Prefer {@link #setGravity(int, int)} once multiple boards exist. */
    public void setGravity(int g) {
        setGravity(0, g);
    }

    public void setGravity(int boardIndex, int g) {
        if (boardIndex >= 0 && boardIndex < boardStates.size()) boardStates.get(boardIndex).setGravity(g);
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

    /** Sets board 0's ability-driven gravity speed factor. Prefer {@link #setGravitySpeedFactor(int, float)}. */
    public void setGlobalGravitySpeedFactor(float factor) {
        setGravitySpeedFactor(0, factor);
    }

    /** Board 0's ability-driven gravity speed factor. Prefer {@link #getGravitySpeedFactor(int)}. */
    public float getGlobalGravitySpeedFactor() {
        return getGravitySpeedFactor(0);
    }

    /** Sets {@code boardIndex}'s ability-driven fall-speed factor (e.g. The Noob's disable/ramp). */
    public void setGravitySpeedFactor(int boardIndex, float factor) {
        if (boardIndex >= 0 && boardIndex < boardStates.size()) boardStates.get(boardIndex).setGravitySpeedFactor(factor);
    }

    public float getGravitySpeedFactor(int boardIndex) {
        return boardStateOrDefault(boardIndex).getGravitySpeedFactor();
    }

    // -------------------------------------------------------------------------
    // Test-only seams
    // -------------------------------------------------------------------------
    // GameHandler.init() always creates exactly one real Board (see class javadoc on the
    // board-scoped refactor); these let tests exercise multi-board combo/gravity isolation
    // without standing up a second real Board.

    /** Test-only: appends an additional {@link BoardState} (no backing {@link Board}) so tests can verify per-board isolation. */
    public void addBoardStateForTesting(int initialGravityMs) {
        BoardState state = new BoardState();
        state.reset(initialGravityMs);
        boardStates.add(state);
    }

    /** Test-only: overrides the slot-to-board/seat mapping to simulate multiple boards without creating them. */
    public void setSlotBoardMappingForTesting(int[] slotBoard, int[] slotSeat) {
        this.slotBoard = slotBoard;
        this.slotSeat = slotSeat;
    }
}
