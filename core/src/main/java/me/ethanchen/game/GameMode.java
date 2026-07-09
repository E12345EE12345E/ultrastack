package me.ethanchen.game;

import me.ethanchen.game.board.Board;

public enum GameMode {

    NONE {
        @Override public GameModeRules rules() { return NONE_RULES; }
    },
    MULTIPLAYER_SCORE {
        @Override public GameModeRules rules() { return SCORE_RULES; }
    },
    MULTIPLAYER_PUZZLE {
        @Override public GameModeRules rules() { return PUZZLE_RULES; }
    };

    /** Returns the rules strategy for this game mode. */
    public abstract GameModeRules rules();

    // -------------------------------------------------------------------------
    // Rule implementations (static singletons, one per non-NONE mode)
    // -------------------------------------------------------------------------

    private static final GameModeRules NONE_RULES = new GameModeRules() {
        @Override public Board.Presets boardPreset(int n) { return Board.Presets.STANDARD_SINGLE; }
        @Override public int initialGravityMs() { return GameConstants.INITIAL_GRAVITY_MS; }
        @Override public void prepareBoard(Board b) {}
        @Override public boolean isWinConditionMet(GameHandler game, long gameEndTargetMs) { return false; }
    };

    private static final GameModeRules SCORE_RULES = new GameModeRules() {
        @Override public Board.Presets boardPreset(int n) {
            if (n == 2) return Board.Presets.STANDARD_DUO;
            if (n == 3) return Board.Presets.STANDARD_TRIO;
            if (n >= 4) return Board.Presets.STANDARD_4P;
            return Board.Presets.STANDARD_SINGLE;
        }
        @Override public int initialGravityMs() { return GameConstants.INITIAL_GRAVITY_MS; }
        @Override public void prepareBoard(Board b) {}
        @Override public boolean isWinConditionMet(GameHandler game, long gameEndTargetMs) {
            return System.currentTimeMillis() >= gameEndTargetMs;
        }
    };

    private static final GameModeRules PUZZLE_RULES = new GameModeRules() {
        @Override public Board.Presets boardPreset(int n) {
            if (n == 2) return Board.Presets.SHORT_DUO;
            if (n == 3) return Board.Presets.SHORT_TRIO;
            if (n >= 4) return Board.Presets.SHORT_4P;
            return Board.Presets.SHORT_SINGLE;
        }
        @Override public int initialGravityMs() { return GameConstants.INITIAL_GRAVITY_PUZZLE_MS; }
        @Override public void prepareBoard(Board b) { b.spawnGarbageLines(GameConstants.PUZZLE_GARBAGE_LINES); }
        @Override public boolean isWinConditionMet(GameHandler game, long gameEndTargetMs) {
            return !game.getBoards().isEmpty() && !game.getBoards().get(0).hasGarbage();
        }
    };
}
