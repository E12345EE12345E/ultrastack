package me.ethanchen.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.board.LineClearResult;

/**
 * Verifies that per-board state introduced by the board-scoped refactor (combo/B2B and gravity)
 * never leaks between boards, using {@link GameHandler}'s test-only seams to simulate a second
 * board without standing up a real {@link me.ethanchen.game.board.Board} (today's only mode of
 * operation creates exactly one).
 */
class GameHandlerBoardIsolationTest {

    private GameHandler newTwoBoardHandler() {
        GameHandler game = new GameHandler(2);
        game.init(GameMode.MULTIPLAYER_SCORE, 0);
        // Board 0 already exists with slot 0 seated on it (from init()); simulate slot 1 sitting
        // on a second, synthetic board so combo/gravity can be checked independently.
        game.addBoardStateForTesting(GameConstants.INITIAL_GRAVITY_MS);
        game.setSlotBoardMappingForTesting(new int[]{0, 1}, new int[]{0, 0});
        return game;
    }

    private LineClearResult tetrisResult(int boardIndex, int playerId) {
        LineClearResult r = new LineClearResult();
        r.placed = true;
        r.playerId = playerId;
        r.boardIndex = boardIndex;
        r.clearedRows = new int[]{0, 1, 2, 3};
        return r;
    }

    @Test
    void comboAndB2bAreIndependentPerBoard() {
        GameHandler game = newTwoBoardHandler();

        // Board 0 gets a clear (combo/B2B should advance); board 1 stays untouched.
        game.applyClearToCounters(tetrisResult(0, 0));

        assertEquals(1, game.getCombo(0));
        assertEquals(1, game.getB2b(0));
        assertEquals(0, game.getCombo(1)); // never touched: still at its reset default
        assertEquals(0, game.getB2b(1));
    }

    @Test
    void comboResetOnOneBoardDoesNotAffectOther() {
        GameHandler game = newTwoBoardHandler();

        game.applyClearToCounters(tetrisResult(0, 0));
        game.applyClearToCounters(tetrisResult(1, 1));
        game.applyClearToCounters(tetrisResult(1, 1));

        assertEquals(1, game.getCombo(0));
        assertEquals(2, game.getCombo(1));

        // A no-clear placement on board 0 resets only board 0's combo.
        LineClearResult noClear = new LineClearResult();
        noClear.placed = true;
        noClear.playerId = 0;
        noClear.boardIndex = 0;
        noClear.clearedRows = new int[0];
        game.applyClearToCounters(noClear);

        assertEquals(0, game.getCombo(0));
        assertEquals(2, game.getCombo(1));
    }

    @Test
    void gravitySpeedFactorIsIndependentPerBoard() {
        GameHandler game = newTwoBoardHandler();

        // Simulate The Noob freezing gravity on board 0 only.
        game.setGravitySpeedFactor(0, 0f);
        game.setGravitySpeedFactor(1, 1f);

        int frozenMs = game.getEffectiveGravityMs(0); // slot 0 -> board 0
        int normalMs = game.getEffectiveGravityMs(1); // slot 1 -> board 1

        assertNotEquals(frozenMs, normalMs);
        assertEquals(Integer.MAX_VALUE / 4, frozenMs);
        assertEquals(game.getGravity(1), normalMs);
    }

    @Test
    void gravityRampAppliesOnlyToItsOwnBoard() {
        GameHandler game = newTwoBoardHandler();
        int initial = game.getGravity(1);

        game.setGravity(0, 42);

        assertEquals(42, game.getGravity(0));
        assertEquals(initial, game.getGravity(1));
    }
}
