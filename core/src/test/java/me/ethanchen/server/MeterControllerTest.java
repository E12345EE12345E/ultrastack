package me.ethanchen.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.board.Piece;
import me.ethanchen.game.progression.CharacterRegistry;

class MeterControllerTest {

    @Test
    void scoreGainFillsEveryPlayersMeterByTheirOwnRate() {
        MeterController mc = new MeterController();
        ActiveLoadout[] loadouts = {
                new ActiveLoadout(CharacterRegistry.byId(0), null, null), // 3-Mino: 1.0x
                new ActiveLoadout(CharacterRegistry.byId(1), null, null), // Wizard: 0.5x
        };
        mc.reset(2, loadouts);

        // Player 0 places a non-bonus piece (O) clearing a line for 1000 points.
        mc.onScoreEvent(0, 1000, Piece.O, true, false);

        assertFalse(mc.tryConsume(0)); // 1000 < 3000 required
        assertFalse(mc.tryConsume(1)); // 500 < 2000 required
    }

    @Test
    void threeMinoPassiveGivesOthersFourTimesMeterOnI3L3Clears() {
        MeterController mc = new MeterController();
        ActiveLoadout[] loadouts = {
                new ActiveLoadout(CharacterRegistry.byId(0), null, null), // placer: 3-Mino
                new ActiveLoadout(CharacterRegistry.byId(0), null, null), // other: also 3-Mino for equal base rate
        };
        mc.reset(2, loadouts);

        // Placer clears with I3 (3-Mino's passive piece): self gets 1x, other gets 4x.
        mc.onScoreEvent(0, 750, Piece.I3, true, false);

        // 750 * 1.0 (self mult) = 750 for placer; 750 * 4.0 = 3000 for other -> meets 3000 requirement.
        assertFalse(mc.tryConsume(0));
        assertTrue(mc.tryConsume(1));
    }

    @Test
    void passiveFillAccumulatesOverTime() {
        MeterController mc = new MeterController();
        ActiveLoadout[] loadouts = { new ActiveLoadout(CharacterRegistry.byId(0), null, null) };
        mc.reset(1, loadouts);

        // 3-Mino: 100/sec, 3000 required -> 30 seconds to fill from passive alone.
        mc.tickPassive(30f);
        assertTrue(mc.tryConsume(0));
    }

    @Test
    void meterCannotExceedRequiredAmount() {
        MeterController mc = new MeterController();
        ActiveLoadout[] loadouts = { new ActiveLoadout(CharacterRegistry.byId(0), null, null) };
        mc.reset(1, loadouts);

        mc.onScoreEvent(0, 1_000_000, Piece.O, true, false);
        assertTrue(mc.tryConsume(0));
        assertFalse(mc.tryConsume(0)); // consumed, resets to 0
    }
}
