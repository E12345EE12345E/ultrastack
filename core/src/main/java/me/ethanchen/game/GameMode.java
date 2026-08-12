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
    },
    /** Same mechanics as {@link #MULTIPLAYER_SCORE}, but characters and artifacts are active (implementation.md, Part 5). */
    CHARACTER_SCORE {
        @Override public GameModeRules rules() { return SCORE_RULES; }
    };

    /** Returns the rules strategy for this game mode. */
    public abstract GameModeRules rules();

    /**
     * True for gamemodes prefixed {@code CHARACTER_}, meaning characters and artifacts affect
     * scoring; {@code MULTIPLAYER_} modes never do, even though artifacts can still be earned
     * from them (implementation.md, Part 5).
     */
    public boolean supportsCharacters() {
        return name().startsWith("CHARACTER_");
    }

    // -------------------------------------------------------------------------
    // Rule implementations (static singletons, one per non-NONE mode)
    // -------------------------------------------------------------------------

    private static final GameModeRules NONE_RULES = new GameModeRules() {
        @Override public Board.Presets boardPreset(int n) { return Board.Presets.STANDARD_SINGLE; }
        @Override public int initialGravityMs() { return GameConstants.INITIAL_GRAVITY_MS; }
        @Override public void prepareBoard(Board b) {}
        @Override public boolean isWinConditionMet(GameHandler game, int boardIndex, long gameEndTargetMs) { return false; }
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
        @Override public boolean isWinConditionMet(GameHandler game, int boardIndex, long gameEndTargetMs) {
            // Shared session-wide clock: every board resolves identically once the timer expires.
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
        @Override public boolean isWinConditionMet(GameHandler game, int boardIndex, long gameEndTargetMs) {
            // Per-board objective: this board wins as soon as its own garbage is cleared,
            // independent of any other board.
            if (boardIndex < 0 || boardIndex >= game.getBoards().size()) return false;
            return !game.getBoards().get(boardIndex).hasGarbage();
        }
    };
}
