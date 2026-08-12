package me.ethanchen.game.pve;

/**
 * Describes a recurring garbage wave applied while a {@link PveSection} is active, ticked by a
 * {@code GarbageIntervalRunner} per board.
 */
public class GarbageInterval {
    /** Milliseconds between each garbage wave once the interval starts firing. */
    public long intervalMs;
    /** Milliseconds to wait after section entry before the first wave fires. */
    public long initialMs;
    public GarbageStyle style = GarbageStyle.DEFAULT;
    /** Number of rows spawned per wave. */
    public int amount = 1;

    public GarbageInterval() {}

    public GarbageInterval(long intervalMs, long initialMs, GarbageStyle style, int amount) {
        this.intervalMs = intervalMs;
        this.initialMs = initialMs;
        this.style = style;
        this.amount = amount;
    }
}
