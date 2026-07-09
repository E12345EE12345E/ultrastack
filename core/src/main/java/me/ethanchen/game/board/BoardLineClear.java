package me.ethanchen.game.board;

/**
 * Extracted from {@link Board}: full-row detection, tile clearing, and row-compaction logic
 * (previously the private {@code clearAndSettle} method).
 *
 * <p>Package-private — callers outside {@code game.board} go through the {@link Board} API.
 */
final class BoardLineClear {

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
        // Map placed cells by row for later per-row column reporting
        java.util.HashMap<Integer, java.util.ArrayList<Integer>> placedByRow = new java.util.HashMap<>();
        for (int[] cell : r.placedCells) {
            placedByRow.computeIfAbsent(cell[1], k -> new java.util.ArrayList<>()).add(cell[0]);
        }

        // Detect full rows
        java.util.ArrayList<Integer> fullRows = new java.util.ArrayList<>();
        for (int y = 0; y < b.height; y++) {
            boolean full = true;
            for (int x = 0; x < b.width; x++) {
                if (b.allowedTiles[y][x] && b.board[y][x].get() == Tile.EMPTY) {
                    full = false;
                    break;
                }
            }
            if (full) fullRows.add(y);
        }

        if (fullRows.isEmpty()) return;

        // Record results
        r.clearedRows = new int[fullRows.size()];
        java.util.HashSet<Integer> clearedRowSet = new java.util.HashSet<>();
        for (int i = 0; i < fullRows.size(); i++) {
            int y = fullRows.get(i);
            r.clearedRows[i] = y;
            clearedRowSet.add(y);

            java.util.ArrayList<Integer> placedCols = placedByRow.getOrDefault(y, new java.util.ArrayList<>());
            r.filledColumnsPerClearedRow.add(placedCols.stream().mapToInt(Integer::intValue).toArray());

            for (int x = 0; x < b.width; x++) {
                if (b.allowedTiles[y][x] && b.board[y][x].get() != Tile.EMPTY) {
                    r.clearedCells.add(new int[]{x, y, b.board[y][x].get()});
                }
            }
        }

        // Row-based compaction: walk bottom-to-top, skip cleared rows, shift remaining down
        int writeY = 0;
        for (int readY = 0; readY < b.height; readY++) {
            if (clearedRowSet.contains(readY)) continue;
            if (readY != writeY) {
                for (int x = 0; x < b.width; x++) {
                    if (b.allowedTiles[writeY][x]) {
                        byte src = b.allowedTiles[readY][x] ? b.board[readY][x].get() : Tile.EMPTY;
                        byte tex = b.allowedTiles[readY][x] ? b.board[readY][x].tex() : Tile.SINGLE_TILE;
                        b.board[writeY][x].set(src, tex);
                    }
                }
            }
            writeY++;
        }
        // Vacate rows above the shifted content
        for (int y = writeY; y < b.height; y++) {
            for (int x = 0; x < b.width; x++) {
                if (b.allowedTiles[y][x]) {
                    b.board[y][x].set(Tile.EMPTY, Tile.SINGLE_TILE);
                }
            }
        }
    }
}
