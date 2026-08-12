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
}
