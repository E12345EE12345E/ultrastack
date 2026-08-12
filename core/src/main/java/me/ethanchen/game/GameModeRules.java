package me.ethanchen.game;

import me.ethanchen.game.board.Board;
import me.ethanchen.game.board.BoardPreset;

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

    /**
     * Returns the geometry for every board that should exist in the session, in board order.
     * Modes with a single shared board (the default for every mode except PvE) just wrap
     * {@link #boardPreset(int)}; PvE overrides this to build one or more custom-geometry boards
     * (splitting players across boards) from its level data.
     */
    default BoardPreset[] boardLayout(int numPlayers) {
        return new BoardPreset[]{ BoardPreset.of(boardPreset(numPlayers)) };
    }

    /**
     * Maps each global session slot (0..numPlayers-1) to the index of the board it is seated on,
     * into the array returned by {@link #boardLayout(int)}. Defaults to every slot on board 0,
     * matching {@link #boardLayout(int)}'s single-board default.
     */
    default int[] slotToBoard(int numPlayers) {
        return new int[numPlayers];
    }

    /** Initial gravity interval in milliseconds. */
    int initialGravityMs();

    /**
     * Performs any one-time board setup after construction (e.g. spawning garbage lines for
     * puzzle mode). Called immediately after the board is created in {@link GameHandler#init}.
     * A no-op for modes without extra setup.
     */
    void prepareBoard(Board board);

    /**
     * Returns true when the win condition for {@code boardIndex} has been satisfied. Modes with
     * a shared session-wide clock (e.g. score mode's timer) ignore {@code boardIndex} and resolve
     * every board identically; modes with a per-board objective (e.g. puzzle's garbage-clear)
     * evaluate only that board.
     *
     * @param game           live game state
     * @param boardIndex     the board being checked
     * @param gameEndTargetMs wall-clock ms at which MULTIPLAYER_SCORE ends (ignored for other modes)
     */
    boolean isWinConditionMet(GameHandler game, int boardIndex, long gameEndTargetMs);
}
