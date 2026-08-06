package me.ethanchen.server;

/**
 * Tracks The Noob's active gravity-disable ability: a shared (non-stacking) 10s disable window,
 * a 5s linear fall-speed ramp back to normal, and stacking passive meter-fill multipliers while
 * gravity is disabled.
 */
final class NoobGravityController {
    static final long DISABLE_MS = 10_000L;
    static final long RAMP_MS = 5_000L;
    static final long TOTAL_MS = DISABLE_MS + RAMP_MS;

    /** Elapsed ms since the current effect window started; {@code -1} when inactive. */
    private long elapsedMs = -1L;
    /** Number of ability activations in the current effect window. */
    private int stacks;

    void reset() {
        elapsedMs = -1L;
        stacks = 0;
    }

    void tick(int deltaMs) {
        if (elapsedMs < 0L) return;
        elapsedMs += Math.max(0, deltaMs);
        if (elapsedMs >= TOTAL_MS) {
            reset();
        }
    }

    /**
     * Records an ability activation. During the disable window only the meter stack grows; during
     * the ramp window the disable timer restarts from zero and the stack still grows.
     */
    void activate() {
        if (elapsedMs < 0L || elapsedMs >= TOTAL_MS) {
            elapsedMs = 0L;
            stacks = 1;
            return;
        }
        if (elapsedMs < DISABLE_MS) {
            stacks++;
            return;
        }
        // Ramping: restart the 10s disable rather than only stacking meter fill.
        elapsedMs = 0L;
        stacks++;
    }

    /**
     * Global fall-speed factor in {@code [0, 1]}: {@code 0} while disabled, lerped during ramp,
     * {@code 1} when inactive.
     */
    float gravitySpeedFactor() {
        if (elapsedMs < 0L) return 1f;
        if (elapsedMs < DISABLE_MS) return 0f;
        float t = (elapsedMs - DISABLE_MS) / (float) RAMP_MS;
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return t;
    }

    /**
     * Passive meter-fill multiplier for all players. {@code 1 + stacks} while gravity is disabled
     * (so 1 activation → 2×, 2 → 3×, …); {@code 1} otherwise.
     */
    float passiveMeterFillMultiplier() {
        if (elapsedMs < 0L || elapsedMs >= DISABLE_MS) return 1f;
        return 1f + stacks;
    }

    boolean isActive() {
        return elapsedMs >= 0L;
    }

    /** Package-visible for tests. */
    long elapsedMs() {
        return elapsedMs;
    }

    /** Package-visible for tests. */
    int stacks() {
        return stacks;
    }
}
