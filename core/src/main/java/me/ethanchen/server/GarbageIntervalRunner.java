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
    /** Per-interval: section-relative time of the next wave (first wave is at {@code initialMs}). */
    private long[] nextDueMs = new long[0];
    private final Random rng = new Random();
    private GarbageStyle lastStyle = GarbageStyle.DEFAULT;

    /** Replaces the active interval set (e.g. on entering a new section) and resets all timers. */
    void reset(GarbageInterval[] newIntervals) {
        intervals = newIntervals != null ? newIntervals : new GarbageInterval[0];
        elapsedMs = new long[intervals.length];
        nextDueMs = new long[intervals.length];
        for (int i = 0; i < intervals.length; i++) {
            nextDueMs[i] = Math.max(0L, intervals[i].initialMs);
        }
    }

    /**
     * Advances every configured interval by {@code deltaMs}. Returns the total number of garbage
     * rows to spawn this tick (0 if none fired); {@link #style()} reports the style to use for
     * that spawn (the last interval that fired this tick, if more than one did).
     * First wave is at {@code initialMs} from section entry; later waves every {@code intervalMs}.
     */
    int tick(int deltaMs) {
        int total = 0;
        for (int i = 0; i < intervals.length; i++) {
            GarbageInterval gi = intervals[i];
            elapsedMs[i] += deltaMs;
            if (gi.intervalMs <= 0) continue;
            while (elapsedMs[i] >= nextDueMs[i]) {
                total += Math.max(0, gi.amount);
                lastStyle = gi.style != null ? gi.style : GarbageStyle.DEFAULT;
                nextDueMs[i] += gi.intervalMs;
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
