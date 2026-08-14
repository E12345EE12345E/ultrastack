package me.ethanchen.game.pve.boss;

/**
 * Preset entrance animation played once the boss lane has fully expanded. Durations are the
 * visible intro only; {@link #LANE_EXPAND_MS} is waited first (boards parting).
 */
public enum BossIntroAnim {
    /** Full-size, fully white for {@link #durationMs}, then normal. */
    FLASH_IN(200),
    /** Ease-out from below the screen at full size. */
    FLOAT_IN(1000),
    /** Ease-out from above the screen at full size. */
    FLOAT_IN_TOP(1000),
    /** Rest pose, alpha 0 → 1. */
    FADE_IN(2000);

    /** Client board-parting duration; {@code ENTERING} waits this long before the intro plays. */
    public static final long LANE_EXPAND_MS = 900;

    public final long durationMs;

    BossIntroAnim(long durationMs) {
        this.durationMs = durationMs;
    }

    /** Total {@code ENTERING} phase length: lane wait plus this preset's visible intro. */
    public long enteringDurationMs() {
        return LANE_EXPAND_MS + durationMs;
    }
}
