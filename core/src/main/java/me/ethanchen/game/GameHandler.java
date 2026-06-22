package me.ethanchen.game;

import java.util.ArrayList;

import me.ethanchen.game.board.Board;
import me.ethanchen.game.board.LineClearResult;
import me.ethanchen.game.board.SpinType;

public class GameHandler {
    private GameMode mode;
    private final int numPlayers;
    private ArrayList<Board> boards;
    private int gravityTickCounter;
    private int gravity;
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
    }

    public void init(GameMode m, long startGameTimer) {
        mode = m;
        startDelay = startGameTimer;
        switch (mode) {
            case NONE:
                break;
            case MULTIPLAYER_SCORE:
                gravity = 1000;
                if (numPlayers == 2) {
                    boards.add(new Board(Board.Presets.STANDARD_DUO));
                } else if (numPlayers == 3) {
                    boards.add(new Board(Board.Presets.STANDARD_TRIO));
                } else if (numPlayers == 4) {
                    boards.add(new Board(Board.Presets.STANDARD_4P));
                }
                break;
        }
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
        switch (mode) {
            case NONE:
                break;
            case MULTIPLAYER_SCORE:
                doGravity(deltaTime);
                doLockTimers(deltaTime);
        }
    }

    private void doGravity(int deltaTime) {
        if (!started) return;
        gravityTickCounter += deltaTime;
        while (gravityTickCounter >= gravity) {
            doGravityTick();
            gravityTickCounter -= gravity;
        }
    }

    public void doGravityTick() {
        for (Board b : boards) {
            b.doGravityTick();
        }
    }

    private void doLockTimers(int deltaTime) {
        if (!started) return;
        for (Board b : boards)
            pendingLockResults.addAll(b.updateLockTimers(deltaTime));
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
     * any spin type, or a 4-line clear.
     */
    public static boolean isB2BEligible(LineClearResult r) {
        return r.spinType != SpinType.NONE || r.numClearedRows() == 4;
    }

    /**
     * Updates the global b2b, combo and previousComboPlayerId counters based on the
     * result of a hard drop.  Must be called AFTER scoring so that pre-clear values
     * can be read during score calculation.
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

    public int getGravity() {
        return gravity;
    }

    public int getB2b() { return b2b; }
    public int getCombo() { return combo; }
    public int getPreviousComboPlayerId() { return previousComboPlayerId; }

    /** Resets the gravity accumulator so the next gravity tick is a full interval away. */
    public void resetGravityTimer() {
        gravityTickCounter = 0;
    }

    public void setGravity(int g) {
        gravity = g;
    }

    public int getGravityTickCounter() {
        return gravityTickCounter;
    }

    public void setGravityTickCounter(int c) {
        gravityTickCounter = c;
    }
}
