package me.ethanchen.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.board.LineClearResult;
import me.ethanchen.game.board.SpinType;

/**
 * Characterization tests for {@link GameHandler}'s universal B2B/combo counter rules,
 * which are shared across game modes and easy to regress when extracting a
 * {@code GameModeRules} strategy in a later refactor phase.
 */
class GameHandlerTest {

    @Test
    void isB2BEligible_trueForSpinOrTetris() {
        LineClearResult tetris = new LineClearResult();
        tetris.clearedRows = new int[]{0, 1, 2, 3};
        tetris.spinType = SpinType.NONE;
        assertTrue(GameHandler.isB2BEligible(tetris));

        LineClearResult tSpin = new LineClearResult();
        tSpin.clearedRows = new int[]{0};
        tSpin.spinType = SpinType.T_SPIN;
        assertTrue(GameHandler.isB2BEligible(tSpin));

        LineClearResult single = new LineClearResult();
        single.clearedRows = new int[]{0};
        single.spinType = SpinType.NONE;
        assertFalse(GameHandler.isB2BEligible(single));
    }

    @Test
    void applyClearToCounters_comboIncrementsOnClearsAndResetsOnMiss() {
        GameHandler handler = new GameHandler(1);

        LineClearResult noClear = placement(false, new int[0], SpinType.NONE, 0);
        handler.applyClearToCounters(noClear);
        assertEquals(0, handler.getCombo());

        LineClearResult single = placement(true, new int[]{0}, SpinType.NONE, 0);
        handler.applyClearToCounters(single);
        assertEquals(1, handler.getCombo());
        assertEquals(0, handler.getB2b(), "single clear is not B2B-eligible");

        LineClearResult tetris = placement(true, new int[]{0, 1, 2, 3}, SpinType.NONE, 0);
        handler.applyClearToCounters(tetris);
        assertEquals(2, handler.getCombo());
        assertEquals(1, handler.getB2b());
        assertEquals(0, handler.getPreviousComboPlayerId());

        LineClearResult miss = placement(true, new int[0], SpinType.NONE, 0);
        handler.applyClearToCounters(miss);
        assertEquals(0, handler.getCombo(), "a placement with no cleared lines resets combo");
    }

    @Test
    void applyClearToCounters_ignoresUnplacedResults() {
        GameHandler handler = new GameHandler(1);
        LineClearResult unplaced = placement(false, new int[]{0, 1, 2, 3}, SpinType.NONE, 0);
        handler.applyClearToCounters(unplaced);
        assertEquals(0, handler.getCombo());
        assertEquals(0, handler.getB2b());
    }

    private static LineClearResult placement(boolean placed, int[] clearedRows, SpinType spin, int playerId) {
        LineClearResult r = new LineClearResult();
        r.placed = placed;
        r.clearedRows = clearedRows;
        r.spinType = spin;
        r.playerId = playerId;
        return r;
    }
}
