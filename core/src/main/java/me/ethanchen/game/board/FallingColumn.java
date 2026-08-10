package me.ethanchen.game.board;

/**
 * A contiguous vertical run of tiles that has detached from the locked board and is
 * falling under gravity. Index 0 of {@link #types} is the bottom tile; connection state
 * is always {@link Tile#SINGLE_TILE} while falling and after landing.
 */
public class FallingColumn {
    /** Stable identity for network soft-reconciliation between snapshots. */
    public int id;
    /** Integer board column; never changes for the lifetime of this column. */
    public int x;
    /** World Y of the bottom tile (fractional while mid-fall between rows). */
    public float bottomY;
    /** Tile types from bottom to top; connection state is always 0. */
    public byte[] types;
    /** Downward speed in tiles/sec (positive = falling down). */
    public float velocity;
    /** False when resting at an integer row this tick (checking whether to continue). */
    public boolean moving;
    /** Player who triggered the fall, or -1 for unattributed (clears lines, scores nothing). */
    public int triggerPlayerId;
    /**
     * True only for split-off airborne minoes of a fall-triggering piece. On first land,
     * these re-run the downward scan once, then clear the flag.
     */
    public boolean pieceTrigger;

    public FallingColumn() {}

    public int bottomRow() {
        return (int) Math.floor(bottomY);
    }

    /**
     * Highest board row this column currently occupies. Mid-interpolation covers one extra
     * row on top so both the start and end cells of the step are solid for piece movement.
     */
    public int topOccupiedRow() {
        int floor = (int) Math.floor(bottomY);
        if (isAtIntegerRow()) {
            return floor + types.length - 1;
        }
        return (int) Math.ceil(bottomY) + types.length - 1;
    }

    /** True when {@link #bottomY} is (numerically) on an integer row. */
    public boolean isAtIntegerRow() {
        float floor = (float) Math.floor(bottomY);
        return bottomY - floor < 1e-4f;
    }

    public boolean occupies(int col, int row) {
        if (col != x || types == null || types.length == 0) return false;
        return row >= bottomRow() && row <= topOccupiedRow();
    }

    /** Number of tiles in this column. */
    public int height() {
        return types == null ? 0 : types.length;
    }
}
