package me.ethanchen.game.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.pve.GarbageStyle;

/**
 * Verifies that {@link GarbageStyle#DEFAULT} / {@link GarbageStyle#DOUBLE_HOLE} share gap
 * columns across every row of an attack, while {@link GarbageStyle#CHEESE} /
 * {@link GarbageStyle#CHEESE_DOUBLE} re-roll per row.
 */
class GarbageStyleSpawnTest {

    /** Returns {@code values} in order from successive {@code nextInt} calls. */
    private static Random sequenceRng(int... values) {
        return new Random() {
            int i = 0;
            @Override
            public int nextInt(int bound) {
                return values[i++];
            }
        };
    }

    private static int emptyCount(Board b, int y) {
        int n = 0;
        for (int x = 0; x < b.bw(); x++) {
            if (b.getBoard()[y][x].get() == Tile.EMPTY) n++;
        }
        return n;
    }

    private static boolean isEmpty(Board b, int x, int y) {
        return b.getBoard()[y][x].get() == Tile.EMPTY;
    }

    @Test
    void defaultSharesOneGapAcrossAllRows() {
        Board b = new Board(Board.Presets.STANDARD_DUO);
        // First draw is the shared gap; later values would have been used if gaps re-rolled.
        b.spawnGarbageRows(3, GarbageStyle.DEFAULT, sequenceRng(2, 7, 4));

        for (int y = 0; y < 3; y++) {
            assertEquals(1, emptyCount(b, y), "row " + y);
            assertEquals(true, isEmpty(b, 2, y), "row " + y + " should keep the shared gap");
        }
    }

    @Test
    void cheeseRerollsGapPerRow() {
        Board b = new Board(Board.Presets.STANDARD_DUO);
        b.spawnGarbageRows(3, GarbageStyle.CHEESE, sequenceRng(2, 7, 4));

        // rows[0] is inserted first and ends up highest (y = amount - 1).
        assertEquals(true, isEmpty(b, 2, 2));
        assertEquals(true, isEmpty(b, 7, 1));
        assertEquals(true, isEmpty(b, 4, 0));
        for (int y = 0; y < 3; y++) {
            assertEquals(1, emptyCount(b, y), "row " + y);
        }
    }

    @Test
    void doubleHoleSharesTwoGapsAcrossAllRows() {
        Board b = new Board(Board.Presets.STANDARD_DUO);
        b.spawnGarbageRows(3, GarbageStyle.DOUBLE_HOLE, sequenceRng(1, 5, 9, 8, 3, 6));

        for (int y = 0; y < 3; y++) {
            assertEquals(2, emptyCount(b, y), "row " + y);
            assertEquals(true, isEmpty(b, 1, y));
            assertEquals(true, isEmpty(b, 5, y));
        }
    }

    @Test
    void cheeseDoubleRerollsTwoGapsPerRow() {
        Board b = new Board(Board.Presets.STANDARD_DUO);
        b.spawnGarbageRows(2, GarbageStyle.CHEESE_DOUBLE, sequenceRng(1, 5, 3, 8));

        assertEquals(true, isEmpty(b, 1, 1));
        assertEquals(true, isEmpty(b, 5, 1));
        assertEquals(true, isEmpty(b, 3, 0));
        assertEquals(true, isEmpty(b, 8, 0));
        assertEquals(2, emptyCount(b, 0));
        assertEquals(2, emptyCount(b, 1));
    }

    @Test
    void customFallsBackToDefaultSharedGap() {
        Board b = new Board(Board.Presets.STANDARD_DUO);
        b.spawnGarbageRows(2, GarbageStyle.CUSTOM, sequenceRng(6, 0));

        for (int y = 0; y < 2; y++) {
            assertEquals(1, emptyCount(b, y), "row " + y);
            assertEquals(true, isEmpty(b, 6, y));
        }
    }
}
