package me.ethanchen.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.GameHandler;
import me.ethanchen.game.GameMode;
import me.ethanchen.game.board.Piece;
import me.ethanchen.game.progression.CharacterAbility;
import me.ethanchen.game.progression.CharacterDef;
import me.ethanchen.network.packets.s2c.gamemode.CharacterModeData;

/**
 * Verifies that {@link MeterController#onScoreEvent} only fills the meters of players seated on
 * the placer's own board (implementation.md, Part 1, scoped by the board-scoped refactor). Uses
 * {@link GameHandler}'s test-only slot mapping seam to simulate two boards of two players each.
 */
class MeterControllerBoardIsolationTest {

    private static final CharacterDef BASIC = new CharacterDef(
            99, "Test", "", "",
            1f /* scoreMeterMultiplier */, 0f /* perSecondMeterFill */, 1000f /* meterRequired */,
            CharacterAbility.FILL_SKYLINE_GAPS, null,
            null, 0f, 1f, 1f, 1f);

    @Test
    void scoreEventOnlyFillsSameBoardMeters() {
        GameHandler game = new GameHandler(4);
        game.init(GameMode.CHARACTER_SCORE, 0);
        // Simulate two 2-player boards: slots {0,1} on board 0, slots {2,3} on board 1.
        game.setSlotBoardMappingForTesting(new int[]{0, 0, 1, 1}, new int[]{0, 1, 0, 1});

        ActiveLoadout[] loadouts = new ActiveLoadout[4];
        for (int i = 0; i < 4; i++) loadouts[i] = new ActiveLoadout(BASIC, null, null);

        MeterController meter = new MeterController();
        meter.reset(4, loadouts, game, 2);

        meter.onScoreEvent(0, 1000L, Piece.T, true, false);

        CharacterModeData data = meter.getCharacterModeData();
        assertTrue(data.meterFill[0] > 0f, "placer's own meter should fill");
        assertTrue(data.meterFill[1] > 0f, "same-board teammate's meter should fill");
        assertEquals(0f, data.meterFill[2], 0.0001f, "other board's meter must not fill");
        assertEquals(0f, data.meterFill[3], 0.0001f, "other board's meter must not fill");
    }

    @Test
    void externalPassiveFillMultiplierIsScopedToItsBoard() {
        GameHandler game = new GameHandler(4);
        game.init(GameMode.CHARACTER_SCORE, 0);
        game.setSlotBoardMappingForTesting(new int[]{0, 0, 1, 1}, new int[]{0, 1, 0, 1});

        ActiveLoadout[] loadouts = new ActiveLoadout[4];
        for (int i = 0; i < 4; i++) loadouts[i] = new ActiveLoadout(BASIC, null, null);

        MeterController meter = new MeterController();
        meter.reset(4, loadouts, game, 2);
        // A per-second fill so tickPassive has something to scale.
        CharacterDef withPassiveFill = new CharacterDef(
                98, "Test2", "", "", 1f, 100f, 1000f,
                CharacterAbility.FILL_SKYLINE_GAPS, null, null, 0f, 1f, 1f, 1f);
        for (int i = 0; i < 4; i++) loadouts[i] = new ActiveLoadout(withPassiveFill, null, null);
        meter.reset(4, loadouts, game, 2);

        // The Noob-style ability doubles passive fill on board 0 only.
        meter.setExternalPassiveFillMultiplier(0, 2f);
        meter.tickPassive(1f);

        CharacterModeData data = meter.getCharacterModeData();
        assertEquals(200f, data.meterFill[0], 0.0001f);
        assertEquals(200f, data.meterFill[1], 0.0001f);
        assertEquals(100f, data.meterFill[2], 0.0001f);
        assertEquals(100f, data.meterFill[3], 0.0001f);
    }
}
