package me.ethanchen.server;

import java.util.Arrays;

import me.ethanchen.game.GameConstants;

/**
 * Optional room-tick timing, enabled with {@code -Dultrastack.tickStats=true}.
 * Records elapsed nanoseconds per {@link GameRoom#tickOnce()} into a ring buffer and dumps
 * mean / p99 / max / overrun count every 5 seconds. No-ops when the property is unset so
 * production pays nothing.
 */
public final class TickInstrumentation {
    public static final boolean ENABLED = Boolean.getBoolean("ultrastack.tickStats");

    private static final TickInstrumentation GLOBAL = new TickInstrumentation("rooms");
    private static final long DUMP_INTERVAL_NS = 5_000_000_000L;
    private static final long OVERRUN_NS = GameConstants.TICK_MS * 1_000_000L;
    private static final int WINDOW = 4096;

    private final String name;
    private final long[] samples = new long[WINDOW];
    private int count;
    private int idx;
    private long overruns;
    private long totalTicks;
    private long maxNs;
    private long lastDumpNs = System.nanoTime();

    private TickInstrumentation(String name) {
        this.name = name;
    }

    /** Records one tick duration. Safe to call from any room-worker thread. */
    public static void record(long elapsedNs) {
        if (!ENABLED) return;
        GLOBAL.recordSample(elapsedNs);
        GLOBAL.maybeDump();
    }

    private synchronized void recordSample(long elapsedNs) {
        samples[idx] = elapsedNs;
        idx = (idx + 1) % WINDOW;
        if (count < WINDOW) count++;
        totalTicks++;
        if (elapsedNs > maxNs) maxNs = elapsedNs;
        if (elapsedNs >= OVERRUN_NS) overruns++;
    }

    private void maybeDump() {
        long now = System.nanoTime();
        synchronized (this) {
            if (now - lastDumpNs < DUMP_INTERVAL_NS || count == 0) return;
            lastDumpNs = now;
            long[] copy = Arrays.copyOf(samples, count);
            long sum = 0;
            for (long s : copy) sum += s;
            Arrays.sort(copy);
            long mean = sum / count;
            int p99i = Math.min(count - 1, (int) (count * 0.99d));
            long p99 = copy[p99i];
            double overrunPct = totalTicks == 0 ? 0d : (100d * overruns) / totalTicks;
            System.out.printf(
                    "[TickStats %s] n=%d window=%d mean=%.2fms p99=%.2fms max=%.2fms overruns=%d (%.2f%%)%n",
                    name, totalTicks, count,
                    mean / 1_000_000d, p99 / 1_000_000d, maxNs / 1_000_000d,
                    overruns, overrunPct);
        }
    }
}
