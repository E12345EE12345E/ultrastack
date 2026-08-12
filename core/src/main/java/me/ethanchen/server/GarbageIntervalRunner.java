package me.ethanchen.server;

import java.util.Random;

import me.ethanchen.game.pve.GarbageInterval;
import me.ethanchen.game.pve.GarbageStyle;

/**
 * Per-board timer that fires the recurring garbage waves configured by the current PvE section's
 * {@link GarbageInterval} list. Reset on every section transition via {@link #reset(GarbageInterval[])}.
 */
class GarbageIntervalRunner {
    private GarbageInterval[] intervals = new GarbageInterval[0];
    private long[] elapsedMs = new long[0];
    /** Per-interval: whether the {@code initialMs} lead-in has already elapsed. */
    private boolean[] started = new boolean[0];
    private final Random rng = new Random();
    private GarbageStyle lastStyle = GarbageStyle.DEFAULT;

    /** Replaces the active interval set (e.g. on entering a new section) and resets all timers. */
    void reset(GarbageInterval[] newIntervals) {
        intervals = newIntervals != null ? newIntervals : new GarbageInterval[0];
        elapsedMs = new long[intervals.length];
        started = new boolean[intervals.length];
    }

    /**
     * Advances every configured interval by {@code deltaMs}. Returns the total number of garbage
     * rows to spawn this tick (0 if none fired); {@link #style()} reports the style to use for
     * that spawn (the last interval that fired this tick, if more than one did).
     */
    int tick(int deltaMs) {
        int total = 0;
        for (int i = 0; i < intervals.length; i++) {
            GarbageInterval gi = intervals[i];
            elapsedMs[i] += deltaMs;
            if (!started[i]) {
                if (elapsedMs[i] < gi.initialMs) continue;
                started[i] = true;
                elapsedMs[i] -= gi.initialMs;
            }
            if (gi.intervalMs <= 0) continue;
            while (elapsedMs[i] >= gi.intervalMs) {
                elapsedMs[i] -= gi.intervalMs;
                total += Math.max(0, gi.amount);
                lastStyle = gi.style != null ? gi.style : GarbageStyle.DEFAULT;
            }
        }
        return total;
    }

    GarbageStyle style() {
        return lastStyle;
    }

    Random rng() {
        return rng;
    }
}
