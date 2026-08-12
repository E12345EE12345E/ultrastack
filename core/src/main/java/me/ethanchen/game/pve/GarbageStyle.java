package me.ethanchen.game.pve;

/** Shape of the rows a {@link GarbageInterval} spawns via {@code Board.spawnGarbageRows}. */
public enum GarbageStyle {
    /** One random single-column gap per row, like the existing puzzle-mode garbage. */
    DEFAULT,
    /** Two random column gaps per row (easier to clear, used for gentler garbage waves). */
    DOUBLE_HOLE,
    /** Reserved for future level-authored custom patterns; falls back to {@link #DEFAULT}. */
    CUSTOM
}
