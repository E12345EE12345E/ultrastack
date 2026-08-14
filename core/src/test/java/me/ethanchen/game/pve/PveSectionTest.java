package me.ethanchen.game.pve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link PveSection} HUD score-target selection. */
class PveSectionTest {

    /** Matches {@code level_0_normal.json} section 4: SCORE 4000 OR (SCORE 500 AND TIME 20000). */
    private static PveSection scoreOrScoreAndTime() {
        PveSection section = new PveSection();
        section.pass = new PveCriterion[][]{
                { new PveCriterion(PveCriterionType.SCORE, 4000) },
                { new PveCriterion(PveCriterionType.SCORE, 500), new PveCriterion(PveCriterionType.TIME, 20000) },
        };
        return section;
    }

    @Test
    void hudShowsLowestUnmetThenHighestOnceAllPassed() {
        PveSection section = scoreOrScoreAndTime();

        assertEquals(500L, section.hudScoreTarget(0));
        assertEquals(500L, section.hudScoreTarget(499));
        assertEquals(4000L, section.hudScoreTarget(500));
        assertEquals(4000L, section.hudScoreTarget(3999));
        assertEquals(4000L, section.hudScoreTarget(4000));
        assertEquals(4000L, section.hudScoreTarget(9999));
    }

    @Test
    void overlayTurnsPassedOnceAnyScoreRequirementIsMet() {
        PveSection section = scoreOrScoreAndTime();

        assertFalse(section.anyScoreRequirementMet(0));
        assertFalse(section.anyScoreRequirementMet(499));
        assertTrue(section.anyScoreRequirementMet(500));
        assertTrue(section.anyScoreRequirementMet(4000));
    }

    @Test
    void noOverlayWhenSectionHasNoScoreRequirement() {
        PveSection timeOnly = new PveSection();
        timeOnly.pass = new PveCriterion[][]{{ new PveCriterion(PveCriterionType.TIME, 10000) }};
        assertEquals(-1L, timeOnly.hudScoreTarget(99999));
        assertFalse(timeOnly.anyScoreRequirementMet(99999));

        PveSection boss = new PveSection();
        boss.pass = new PveCriterion[0][];
        assertEquals(-1L, boss.hudScoreTarget(0));
        assertFalse(boss.anyScoreRequirementMet(0));
    }

    @Test
    void singleScoreRequirementStaysTheTarget() {
        PveSection section = new PveSection();
        section.pass = new PveCriterion[][]{{ new PveCriterion(PveCriterionType.SCORE, 1200) }};
        assertEquals(1200L, section.hudScoreTarget(0));
        assertEquals(1200L, section.hudScoreTarget(1200));
        assertFalse(section.anyScoreRequirementMet(1199));
        assertTrue(section.anyScoreRequirementMet(1200));
    }
}
