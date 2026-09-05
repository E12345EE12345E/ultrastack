package me.ethanchen.game.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link Board#triggerOverhangFall} converts locked tiles above column gaps
 * into falling columns and leaves solidly supported stacks untouched.
 */
class OverhangFallTest {

    private static final int PLAYER = 0;
    private static final int COL = 4;
    private static final int OTHER_COL = 6;

    private static void setSolid(Board b, int x, int y, byte type) {
        b.getBoard()[y][x].set(type, Tile.SINGLE_TILE);
    }

    private static byte cell(Board b, int x, int y) {
        return b.getBoard()[y][x].get();
    }

    private static FallingColumn columnAt(Board b, int x, float bottomY) {
        for (FallingColumn col : b.getFallingColumns()) {
            if (col.x == x && Math.abs(col.bottomY - bottomY) < 1e-4f) return col;
        }
        return null;
    }

    @Test
    void solidStackUnchanged() {
        Board b = new Board(Board.Presets.STANDARD_DUO);
        for (int y = 0; y <= 4; y++) setSolid(b, COL, y, Tile.GARBAGE);

        b.triggerOverhangFall(PLAYER);

        assertTrue(b.getFallingColumns().isEmpty());
        for (int y = 0; y <= 4; y++) {
            assertEquals(Tile.GARBAGE, cell(b, COL, y), "row " + y);
        }
    }

    @Test
    void gapInTheMiddleDetachesTilesAbove() {
        Board b = new Board(Board.Presets.STANDARD_DUO);
        setSolid(b, COL, 0, Tile.GARBAGE);
        setSolid(b, COL, 1, Tile.GARBAGE);
        // row 2 empty
        setSolid(b, COL, 3, Tile.I);
        setSolid(b, COL, 4, Tile.J);
        setSolid(b, COL, 5, Tile.L);

        b.triggerOverhangFall(PLAYER);

        assertEquals(Tile.GARBAGE, cell(b, COL, 0));
        assertEquals(Tile.GARBAGE, cell(b, COL, 1));
        assertEquals(Tile.EMPTY, cell(b, COL, 3));
        assertEquals(Tile.EMPTY, cell(b, COL, 4));
        assertEquals(Tile.EMPTY, cell(b, COL, 5));

        assertEquals(1, b.getFallingColumns().size());
        FallingColumn col = columnAt(b, COL, 3f);
        assertNotNull(col);
        assertEquals(3, col.height());
        assertEquals(Tile.I, col.types[0]);
        assertEquals(Tile.J, col.types[1]);
        assertEquals(Tile.L, col.types[2]);
        assertEquals(PLAYER, col.triggerPlayerId);
        assertEquals(false, col.pieceTrigger);
        assertEquals(false, col.moving);
    }

    @Test
    void twoGapsProduceTwoFallingColumns() {
        Board b = new Board(Board.Presets.STANDARD_DUO);
        setSolid(b, COL, 0, Tile.GARBAGE);
        // row 1 empty
        setSolid(b, COL, 2, Tile.O);
        // row 3 empty
        setSolid(b, COL, 4, Tile.S);
        setSolid(b, COL, 5, Tile.Z);

        b.triggerOverhangFall(PLAYER);

        assertEquals(Tile.GARBAGE, cell(b, COL, 0));
        assertEquals(Tile.EMPTY, cell(b, COL, 2));
        assertEquals(Tile.EMPTY, cell(b, COL, 4));
        assertEquals(Tile.EMPTY, cell(b, COL, 5));

        assertEquals(2, b.getFallingColumns().size());
        FallingColumn lower = columnAt(b, COL, 2f);
        assertNotNull(lower);
        assertEquals(1, lower.height());
        assertEquals(Tile.O, lower.types[0]);

        FallingColumn upper = columnAt(b, COL, 4f);
        assertNotNull(upper);
        assertEquals(2, upper.height());
        assertEquals(Tile.S, upper.types[0]);
        assertEquals(Tile.Z, upper.types[1]);
    }

    @Test
    void tilesOnEmptyFloorAllFall() {
        Board b = new Board(Board.Presets.STANDARD_DUO);
        // row 0 empty
        setSolid(b, COL, 1, Tile.T);
        setSolid(b, COL, 2, Tile.T);
        setSolid(b, COL, 3, Tile.T);

        b.triggerOverhangFall(PLAYER);

        assertEquals(Tile.EMPTY, cell(b, COL, 1));
        assertEquals(Tile.EMPTY, cell(b, COL, 2));
        assertEquals(Tile.EMPTY, cell(b, COL, 3));

        assertEquals(1, b.getFallingColumns().size());
        FallingColumn col = columnAt(b, COL, 1f);
        assertNotNull(col);
        assertEquals(3, col.height());
        assertEquals(Tile.T, col.types[0]);
        assertEquals(Tile.T, col.types[1]);
        assertEquals(Tile.T, col.types[2]);
    }

    @Test
    void supportedNeighborColumnIsUntouched() {
        Board b = new Board(Board.Presets.STANDARD_DUO);
        for (int y = 0; y <= 3; y++) setSolid(b, OTHER_COL, y, Tile.GARBAGE);
        setSolid(b, COL, 0, Tile.GARBAGE);
        // row 1 empty in COL
        setSolid(b, COL, 2, Tile.I);
        setSolid(b, COL, 3, Tile.I);

        b.triggerOverhangFall(PLAYER);

        for (int y = 0; y <= 3; y++) {
            assertEquals(Tile.GARBAGE, cell(b, OTHER_COL, y), "supported col row " + y);
        }
        assertEquals(Tile.GARBAGE, cell(b, COL, 0));
        assertEquals(Tile.EMPTY, cell(b, COL, 2));
        assertEquals(Tile.EMPTY, cell(b, COL, 3));

        assertEquals(1, b.getFallingColumns().size());
        FallingColumn col = columnAt(b, COL, 2f);
        assertNotNull(col);
        assertEquals(2, col.height());
        assertEquals(COL, col.x);
    }
}
