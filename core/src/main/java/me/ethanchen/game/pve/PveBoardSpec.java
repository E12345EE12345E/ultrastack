package me.ethanchen.game.pve;

/**
 * Custom board geometry for one board of a PvE level, for a specific seat count (1 or 2 seats).
 * Converted into an engine {@code BoardPreset} via {@code BoardPreset.fromPve}.
 */
public class PveBoardSpec {
    public int width;
    public int height;
    /** One {@code [x, y]} pair per seat, in seat order. */
    public int[][] spawns = new int[0][];
    /** {@code [x, y]} cells that are not part of the playable field (walls / cutouts). */
    public int[][] blockedTiles = new int[0][];
    /** {@code [x, y]} cells pre-filled with a single garbage tile when the board is prepared. */
    public int[][] initialTiles = new int[0][];

    public PveBoardSpec() {}
}
