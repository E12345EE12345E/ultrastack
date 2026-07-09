package me.ethanchen.game;

import me.ethanchen.game.board.Board;

/**
 * Strategy interface that encodes all mode-specific behaviour that used to be scattered across
 * {@code switch (gameMode)} blocks in {@link GameHandler} and the server layer.
 *
 * <p>Each {@link GameMode} constant carries an implementation; obtain it via
 * {@link GameMode#rules()}.
 */
public interface GameModeRules {

    /**
     * Returns the board preset for this mode and the given player count.
     * {@link GameMode#NONE} should never be called for; it returns
     * {@link Board.Presets#STANDARD_SINGLE} as a safe fallback.
     */
    Board.Presets boardPreset(int numPlayers);

    /** Initial gravity interval in milliseconds. */
    int initialGravityMs();

    /**
     * Performs any one-time board setup after construction (e.g. spawning garbage lines for
     * puzzle mode). Called immediately after the board is created in {@link GameHandler#init}.
     * A no-op for modes without extra setup.
     */
    void prepareBoard(Board board);

    /**
     * Returns true when the win condition for this mode has been satisfied.
     *
     * @param game           live game state
     * @param gameEndTargetMs wall-clock ms at which MULTIPLAYER_SCORE ends (ignored for other modes)
     */
    boolean isWinConditionMet(GameHandler game, long gameEndTargetMs);
}
