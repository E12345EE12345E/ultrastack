package me.ethanchen.game.pve;

/** Shape of the rows a {@link GarbageInterval} spawns via {@code Board.spawnGarbageRows}. */
public enum GarbageStyle {
    /** One random single-column gap, the same x for every row in the attack. */
    DEFAULT,
    /** One random single-column gap per row (classic cheese). */
    CHEESE,
    /** Two random column gaps, the same x positions for every row in the attack. */
    DOUBLE_HOLE,
    /** Two random column gaps per row. */
    CHEESE_DOUBLE,
    /** Reserved for future level-authored custom patterns; falls back to {@link #DEFAULT}. */
    CUSTOM
}
