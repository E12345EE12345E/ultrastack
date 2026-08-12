package me.ethanchen.game.pve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Verifies {@link PveLevelLoader#fromJson} round-trips the level JSON format described by the plan. */
class PveLevelLoaderTest {

    private static final String LEVEL_0_JSON = "{"
            + "\"sections\": ["
            + "  {\"pass\": [[{\"type\":\"SCORE\",\"value\":4000}],[{\"type\":\"SCORE\",\"value\":2500},{\"type\":\"TIME\",\"value\":60000}]],"
            + "   \"timeoutMs\": 60000},"
            + "  {\"pass\": [], \"env\": {\"bossId\": 0}}"
            + "],"
            + "\"difficultyRank\": 0,"
            + "\"board1\": {\"width\": 10, \"height\": 24, \"spawns\": [[4, 20]]},"
            + "\"board2\": {\"width\": 10, \"height\": 24, \"spawns\": [[1, 20],[7, 20]]}"
            + "}";

    @Test
    void parsesSectionsAndCriteria() {
        PveLevelData level = PveLevelLoader.fromJson(LEVEL_0_JSON);

        assertEquals(2, level.sections.length);
        PveSection first = level.sections[0];
        assertEquals(60000L, first.timeoutMs);
        assertEquals(2, first.pass.length);
        assertEquals(1, first.pass[0].length);
        assertEquals(PveCriterionType.SCORE, first.pass[0][0].type);
        assertEquals(4000L, first.pass[0][0].value);
        assertEquals(2, first.pass[1].length);
        assertEquals(PveCriterionType.TIME, first.pass[1][1].type);
    }

    @Test
    void parsesBossSectionAndEnvironment() {
        PveLevelData level = PveLevelLoader.fromJson(LEVEL_0_JSON);
        PveSection boss = level.sections[1];
        assertEquals(0, boss.pass.length);
        assertEquals(0, boss.env.bossId);
        assertNull(boss.env.gravityMs);
    }

    @Test
    void parsesBoardGeometry() {
        PveLevelData level = PveLevelLoader.fromJson(LEVEL_0_JSON);
        assertEquals(10, level.board1.width);
        assertEquals(1, level.board1.spawns.length);
        assertEquals(2, level.board2.spawns.length);
        assertEquals(level.board1, level.boardSpecFor(1));
        assertEquals(level.board2, level.boardSpecFor(2));
    }
}
