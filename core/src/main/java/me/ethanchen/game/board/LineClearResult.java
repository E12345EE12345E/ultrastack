package me.ethanchen.game.board;

import java.util.ArrayList;

/**
 * Returned by {@link Board#hardDrop(int)} with everything that happened during the drop:
 * whether the piece was locked, which cells were placed/cleared/broken, and which rows were cleared.
 */
public class LineClearResult {
    /** True when the piece was actually locked into the board (had solid support). */
    public boolean placed;

    /** Player index whose piece was hard-dropped. */
    public int playerId;

    /** Piece type byte of the dropped piece. */
    public byte pieceType;

    /**
     * Integer board-column of the piece anchor after it came to rest.
     * Not meaningful when {@code placed == false} and the piece is mid-air.
     */
    public int restingX;
    public int restingY;

    /**
     * Exact floating-point center of the piece anchor after it came to rest.
     * Unlike {@code restingX/Y}, this is not floored, so I/O pieces (which sit on 0.5
     * boundaries) retain their true center column for repeat-column calculations.
     */
    public float restingCenterX;
    public float restingCenterY;

    /** Rotation state (0–3) of the piece at the moment of placement. */
    public byte pieceRotation;

    /**
     * Every board cell written by the lock ({@code allowedTiles=true} cells only).
     * Each entry is {@code {x, y}}.
     */
    public ArrayList<int[]> placedCells = new ArrayList<>();

    /** Rows that were completely filled and cleared (ascending order). */
    public int[] clearedRows = new int[0];

    /**
     * For each cleared row (same order as {@code clearedRows}), the x-coordinates
     * within that row that were contributed by the piece just placed.
     * Each entry is an {@code int[]} of x values.
     */
    public ArrayList<int[]> filledColumnsPerClearedRow = new ArrayList<>();

    /**
     * Every board cell that was cleared (only {@code allowedTiles=true} tiles, captured
     * before the clear).  Each entry is {@code {x, y, tileType}}.
     */
    public ArrayList<int[]> clearedCells = new ArrayList<>();

    /**
     * Cells where a mino landed on an {@code allowedTiles=false} position and could not
     * be written to the board.  Each entry is {@code {x, y, tileType}}.
     */
    public ArrayList<int[]> brokenCells = new ArrayList<>();

    /** Spin type detected at the moment of placement. */
    public SpinType spinType = SpinType.NONE;

    /** Convenience: number of rows cleared by this drop. */
    public int numClearedRows() {
        return clearedRows.length;
    }
}
