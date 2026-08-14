package me.ethanchen.game.pve;

/**
 * One stage of a PvE level. {@code pass} is an OR-of-ANDs: the section is cleared when at least
 * one inner array has every one of its criteria satisfied. An empty {@code pass} array marks a
 * boss section, which instead advances when {@code BossController} reports the boss defeated.
 */
public class PveSection {
    /** Outer array = OR, inner array = AND. Empty = boss section. */
    public PveCriterion[][] pass = new PveCriterion[0][];
    /** Milliseconds after which the section's criteria are re-checked once more before failing the run; {@code -1} = no timeout. */
    public long timeoutMs = -1;
    public PveEnvironment env = new PveEnvironment();
    public PveBoardDisplay display = PveBoardDisplay.BOARD_DEFAULT;

    public PveSection() {}

    public boolean isBossSection() {
        return pass == null || pass.length == 0;
    }

    public boolean hasTimeout() {
        return timeoutMs >= 0;
    }

    /**
     * Score shown on the playfield overlay: the lowest unmet {@link PveCriterionType#SCORE}
     * requirement, or the highest SCORE requirement once every one is already met. Returns
     * {@code -1} when this section has no SCORE criteria (nothing should be drawn).
     */
    public long hudScoreTarget(long sectionScore) {
        long lowestUnmet = Long.MAX_VALUE;
        long highest = -1L;
        if (pass == null) return -1L;
        for (PveCriterion[] and : pass) {
            if (and == null) continue;
            for (PveCriterion c : and) {
                if (c == null || c.type != PveCriterionType.SCORE) continue;
                if (c.value > highest) highest = c.value;
                if (sectionScore < c.value && c.value < lowestUnmet) lowestUnmet = c.value;
            }
        }
        if (highest < 0L) return -1L;
        return lowestUnmet == Long.MAX_VALUE ? highest : lowestUnmet;
    }

    /** True when at least one SCORE criterion in this section is already satisfied. */
    public boolean anyScoreRequirementMet(long sectionScore) {
        if (pass == null) return false;
        for (PveCriterion[] and : pass) {
            if (and == null) continue;
            for (PveCriterion c : and) {
                if (c == null || c.type != PveCriterionType.SCORE) continue;
                if (sectionScore >= c.value) return true;
            }
        }
        return false;
    }
}
