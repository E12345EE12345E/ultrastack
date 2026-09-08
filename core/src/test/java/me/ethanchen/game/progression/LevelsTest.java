package me.ethanchen.game.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.GameMode;

class LevelsTest {

    @Test
    void levelFromXp_matchesWebFormula() {
        assertEquals(1, Levels.levelFromXp(0));
        assertEquals(1, Levels.levelFromXp(99));
        assertEquals(2, Levels.levelFromXp(100));
        assertEquals(2, Levels.levelFromXp(399));
        assertEquals(3, Levels.levelFromXp(400));
        assertEquals(10, Levels.levelFromXp(9001));
        assertEquals(1, Levels.levelFromXp(-10));
    }

    @Test
    void bestGameRecord_comparesRawScore() {
        BestGameRecord first = new BestGameRecord("a", "MULTIPLAYER_SCORE", 100, "100");
        assertTrue(first.isStrictlyBetterThan(null));
        assertFalse(first.isStrictlyBetterThan(new BestGameRecord("b", "MULTIPLAYER_SCORE", 100, "100")));
        assertTrue(new BestGameRecord("c", "MULTIPLAYER_SCORE", 101, "101").isStrictlyBetterThan(first));
    }

    @Test
    void tracksBests_onlyScorePuzzleAndCharacterScore() {
        assertTrue(BestGameRecord.tracksBests(GameMode.MULTIPLAYER_SCORE));
        assertTrue(BestGameRecord.tracksBests(GameMode.MULTIPLAYER_PUZZLE));
        assertTrue(BestGameRecord.tracksBests(GameMode.CHARACTER_SCORE));
        assertFalse(BestGameRecord.tracksBests(GameMode.PVE));
        assertFalse(BestGameRecord.tracksBests(GameMode.NONE));
        assertFalse(BestGameRecord.tracksBests(null));
    }

    @Test
    void hoverText_includesDateAndPlayerNames() {
        BestGameRecord rec = new BestGameRecord("g", "MULTIPLAYER_SCORE", 100, "100");
        rec.timestampMs = 1_700_000_000_000L;
        rec.playerNames = new String[]{ "alice", "bob" };
        String hover = rec.hoverText();
        assertTrue(hover.startsWith("Played: "));
        assertTrue(hover.contains("\nPlayers: alice, bob"));
    }
}
