package me.ethanchen.game.pve;

/**
 * Per-section environment overrides. Boxed/nullable fields mean "unset -&gt; inherit the
 * previous section's (or the level default's) value"; non-nullable fields always have an
 * explicit default that means "no override" ({@code bossId = -1}, {@code garbage = []}).
 */
public class PveEnvironment {
    /** Gravity interval override in ms/row, or {@code null} to keep whatever was already active. */
    public Integer gravityMs;
    /** Ability/passive-independent gravity speed multiplier override, or {@code null} to keep the current one. */
    public Float gravitySpeedFactor;
    /** Boss id for a boss section (see {@code BossRegistry}), or {@code -1} for none. */
    public int bossId = -1;
    public GarbageInterval[] garbage = new GarbageInterval[0];

    public PveEnvironment() {}

    public boolean hasBoss() {
        return bossId >= 0;
    }
}
