package me.ethanchen.network.packets.s2c;

/**
 * Compact particle spawn descriptor sent in place of individual {@link NetParticle} objects
 * for bulk effects (line-clear tile-break, ability hard-drop flashes). One spawner object
 * replaces the many per-cell NetParticles previously required.
 *
 * <p>{@link #TYPE_LINE_CLEAR} encodes one cleared row: the y-coordinate and a tile-type
 * array of length board_width. A value of {@code -1} means no particle at that column
 * (the cell was empty or on an {@code allowedTiles=false} position). The client emits
 * a TILE_BREAK particle for each column that is not {@code -1}.
 *
 * <p>{@link #TYPE_HARD_DROP_CELLS} encodes an arbitrary set of board cells that should each
 * receive a hard-drop FLASH particle (used by 3-Mino's skyline-fill ability).
 *
 * <p>The piece-placement hard-drop flash previously defined here ({@code TYPE_HARD_DROP}) has
 * moved to {@link me.ethanchen.network.dto.HardDropEffect}, sent via
 * {@link HardDropEffectsBroadcast}.
 */
public class ParticleSpawner {

    /**
     * Line-clear tile-break: one TILE_BREAK particle per non-(-1) entry in {@link #tileIds}
     * for the row at {@link #lineY}.
     */
    public static final byte TYPE_LINE_CLEAR = 1;

    /**
     * Hard-drop flash at specific cells: one FLASH particle per entry in the parallel
     * {@link #cellXs}/{@link #cellYs} arrays.
     */
    public static final byte TYPE_HARD_DROP_CELLS = 2;

    // ----- common -----

    /** Which spawner type this is ({@link #TYPE_LINE_CLEAR} or {@link #TYPE_HARD_DROP_CELLS}). */
    public byte spawnerType;

    /** Index into the {@code boards[]} array that these particles belong to. */
    public byte boardIndex;

    // ----- TYPE_LINE_CLEAR fields -----

    /**
     * Tile-type byte for each column of the cleared row, indexed by board x.
     * A value of {@code -1} means no TILE_BREAK particle should be spawned at that column
     * (the position was empty or on an {@code allowedTiles=false} cell).
     * Length equals the board width.
     */
    public byte[] tileIds;

    /** Board y-coordinate of the cleared row. */
    public byte lineY;

    // ----- TYPE_HARD_DROP_CELLS fields -----

    /** Board x-coordinates of cells that should flash; parallel to {@link #cellYs}. */
    public byte[] cellXs;

    /** Board y-coordinates of cells that should flash; parallel to {@link #cellXs}. */
    public byte[] cellYs;
}
