package me.ethanchen.game.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.pve.GarbageStyle;

/**
 * Verifies that {@link Board#spawnGarbageRows} moves active pieces and falling columns only as
 * far as the rising garbage actually requires, instead of shifting everything up by the full
 * spawn amount.
 */
class GarbageRowPushTest {

    /** Column every inserted garbage row leaves its hole in, via {@link #fixedGapRng()}. */
    private static final int GAP_COLUMN = 3;

    private static Random fixedGapRng() {
        return new Random() {
            @Override
            public int nextInt(int bound) {
                return GAP_COLUMN;
            }
        };
    }

    /** Horizontal I piece occupying row {@code row} in columns 4..7 (clear of the hole). */
    private static Piece horizontalI(int row) {
        Piece p = Piece.I();
        p.location.set(5.5f, row - 0.5f);
        return p;
    }

    private static float rowOf(Piece horizontalI) {
        return horizontalI.location.y + 0.5f;
    }

    private static FallingColumn garbageColumn(int x, float bottomY) {
        FallingColumn col = new FallingColumn();
        col.x = x;
        col.bottomY = bottomY;
        col.types = new byte[]{Tile.GARBAGE};
        col.triggerPlayerId = -1;
        return col;
    }

    @Test
    void pieceAboveTheGarbageDoesNotMove() {
        Board b = new Board(Board.Presets.STANDARD_DUO);
        Piece p = horizontalI(12);
        b.getActivePieces().add(p);

        b.spawnGarbageRows(4, GarbageStyle.DEFAULT, fixedGapRng());

        assertEquals(12f, rowOf(p), 1e-4f);
    }

    @Test
    void pieceIsPushedOnlyAsFarAsTheGarbageReaches() {
        Board b = new Board(Board.Presets.STANDARD_DUO);
        Piece p = horizontalI(2);
        b.getActivePieces().add(p);

        // Garbage ends up filling rows 0..3, so the piece has to give up exactly two rows.
        b.spawnGarbageRows(4, GarbageStyle.DEFAULT, fixedGapRng());

        assertEquals(4f, rowOf(p), 1e-4f);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < b.bw(); x++) {
                byte expected = x == GAP_COLUMN ? Tile.EMPTY : Tile.GARBAGE;
                assertEquals(expected, b.getBoard()[y][x].get(), "row " + y + " col " + x);
            }
        }
    }

    @Test
    void pieceSittingOverTheHoleIsNotPushed() {
        Board b = new Board(Board.Presets.STANDARD_DUO);
        Piece p = Piece.I();
        p.rotateCW(); // vertical, all four minos in one column
        p.location.set(GAP_COLUMN - 0.5f, 1.5f); // column GAP_COLUMN, rows 0..3
        b.getActivePieces().add(p);

        b.spawnGarbageRows(4, GarbageStyle.DEFAULT, fixedGapRng());

        assertEquals(1.5f, p.location.y, 1e-4f);
    }

    @Test
    void pushedPieceChainPushesThePieceAboveIt() {
        Board b = new Board(Board.Presets.STANDARD_DUO);
        Piece lower = horizontalI(0);
        Piece upper = horizontalI(1);
        b.getActivePieces().add(lower);
        b.getActivePieces().add(upper);

        // One garbage row only touches the lower piece; the upper one moves to make room.
        b.spawnGarbageRows(1, GarbageStyle.DEFAULT, fixedGapRng());

        assertEquals(1f, rowOf(lower), 1e-4f);
        assertEquals(2f, rowOf(upper), 1e-4f);
    }

    @Test
    void fallingColumnsMoveOnlyWhenTheGarbageReachesThem() {
        Board b = new Board(Board.Presets.STANDARD_DUO);
        FallingColumn low = garbageColumn(5, 0f);
        FallingColumn high = garbageColumn(5, 10f);
        b.getFallingColumns().add(low);
        b.getFallingColumns().add(high);

        b.spawnGarbageRows(2, GarbageStyle.DEFAULT, fixedGapRng());

        assertEquals(2f, low.bottomY, 1e-4f);
        assertEquals(10f, high.bottomY, 1e-4f);
    }
}
