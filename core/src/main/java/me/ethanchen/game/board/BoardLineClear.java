package me.ethanchen.game.board;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Extracted from {@link Board}: full-row detection, tile clearing, and row-compaction logic
 * (previously the private {@code clearAndSettle} method).
 *
 * <p>Package-private — callers outside {@code game.board} go through the {@link Board} API.
 */
final class BoardLineClear {

    private static final int[] EMPTY_COLS = new int[0];

    private BoardLineClear() {}

    /**
     * Detects full rows, records cleared cells and columns into {@code r}, clears those rows,
     * then compacts the board downward to fill the gaps.
     *
     * <p>A row is considered full when every column is either a non-empty board tile or an
     * {@code allowedTiles=false} permanent feature. Row compaction preserves relative horizontal
     * order and never overwrites permanent ({@code allowedTiles=false}) cells.
     */
    static void clearAndSettle(Board b, LineClearResult r) {
        int[] placedCount = new int[b.height];
        for (int[] cell : r.placedCells) {
            int y = cell[1];
            if (y >= 0 && y < b.height) placedCount[y]++;
        }
        int[][] placedCols = new int[b.height][];
        int[] placedWrite = new int[b.height];
        for (int y = 0; y < b.height; y++) {
            if (placedCount[y] > 0) placedCols[y] = new int[placedCount[y]];
        }
        for (int[] cell : r.placedCells) {
            int y = cell[1];
            if (y >= 0 && y < b.height && placedCols[y] != null) {
                placedCols[y][placedWrite[y]++] = cell[0];
            }
        }

        boolean[] cleared = new boolean[b.height];
        ArrayList<Integer> fullRows = new ArrayList<>();
        for (int y = 0; y < b.height; y++) {
            boolean full = true;
            for (int x = 0; x < b.width; x++) {
                if (b.allowedTiles[y][x] && b.tileTypeAt(x, y) == Tile.EMPTY) {
                    full = false;
                    break;
                }
            }
            if (full) {
                fullRows.add(y);
                cleared[y] = true;
            }
        }

        if (fullRows.isEmpty()) return;

        r.clearedRows = new int[fullRows.size()];
        for (int i = 0; i < fullRows.size(); i++) {
            int y = fullRows.get(i);
            r.clearedRows[i] = y;
            r.filledColumnsPerClearedRow.add(placedCols[y] != null ? placedCols[y] : EMPTY_COLS);

            for (int x = 0; x < b.width; x++) {
                if (b.allowedTiles[y][x] && b.tileTypeAt(x, y) != Tile.EMPTY) {
                    r.clearedCells.add(new int[]{x, y, b.tileTypeAt(x, y)});
                }
            }
        }

        // Push other players' active pieces out of the way of descending overhangs before the
        // locked-tile grid itself is compacted (see BoardPiecePush for why this must happen
        // per-row rather than as a single bulk shift).
        BoardPiecePush.pushPiecesUnderOverhangs(b, fullRows);

        compactRows(b, cleared);
        r.allClear = b.isAllClear();
    }

    private static void compactRows(Board b, boolean[] cleared) {
        int writeY = 0;
        if (b.fullyAllowed) {
            int w = b.width;
            for (int readY = 0; readY < b.height; readY++) {
                if (cleared[readY]) continue;
                if (readY != writeY) {
                    System.arraycopy(b.tileTypes, readY * w, b.tileTypes, writeY * w, w);
                    System.arraycopy(b.tileTex, readY * w, b.tileTex, writeY * w, w);
                }
                writeY++;
            }
            int from = writeY * w;
            Arrays.fill(b.tileTypes, from, b.tileTypes.length, Tile.EMPTY);
            Arrays.fill(b.tileTex, from, b.tileTex.length, Tile.SINGLE_TILE);
            b.recountFilled();
            return;
        }
        for (int readY = 0; readY < b.height; readY++) {
            if (cleared[readY]) continue;
            if (readY != writeY) {
                for (int x = 0; x < b.width; x++) {
                    if (b.allowedTiles[writeY][x]) {
                        byte src = b.allowedTiles[readY][x] ? b.tileTypeAt(x, readY) : Tile.EMPTY;
                        byte tex = b.allowedTiles[readY][x] ? b.tileTexAt(x, readY) : Tile.SINGLE_TILE;
                        b.writeTileUnchecked(x, writeY, src, tex);
                    }
                }
            }
            writeY++;
        }
        for (int y = writeY; y < b.height; y++) {
            for (int x = 0; x < b.width; x++) {
                if (b.allowedTiles[y][x]) {
                    b.writeTileUnchecked(x, y, Tile.EMPTY, Tile.SINGLE_TILE);
                }
            }
        }
        b.recountFilled();
    }
}
