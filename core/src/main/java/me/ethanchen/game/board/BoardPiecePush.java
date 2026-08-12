package me.ethanchen.game.board;

import java.util.ArrayList;

import com.badlogic.gdx.math.Vector2;

/**
 * Pushes active pieces and falling columns out of the way of solid tiles that move vertically
 * underneath them: down when a descending overhang (locked tiles compacting downward during a
 * line clear) would otherwise intersect or phase through them, and up when inserted garbage
 * rows raise the whole stack into them.
 *
 * <p>Both cases are resolved one row at a time against a boolean occupancy snapshot rather
 * than by resolving the net shift in one shot, because a single row step moves solid content
 * by exactly one cell: a piece that did not overlap solid tiles before the step can always be
 * resolved by moving one cell, so the "crushed between the moving tiles and the stack" case is
 * unreachable. A thin overhang must "catch" a piece on each individual row it passes through,
 * not just on the total displacement, and a piece the garbage never touches must not move at
 * all even when several rows are inserted at once.
 *
 * <p>The line-clear direction additionally relies on an invariant of {@link BoardLineClear}:
 * a row can only be cleared when every allowed cell in it holds a locked tile, so no active
 * piece can ever have a mino inside a cleared row.
 *
 * <p>Falling columns are treated analogously: their footprint (integer rows from
 * {@link FallingColumn#bottomRow()} through {@code bottomRow + height - 1}) is pushed under
 * the same per-row loop.
 */
final class BoardPiecePush {

    /** Sign of a push, in the "shift down by N" convention used by the overlap helpers. */
    private static final int DOWN = 1;
    private static final int UP = -1;

    private BoardPiecePush() {}

    /**
     * Pushes every eligible active piece and falling column down by however much is needed to
     * stay clear of descending overhangs as the rows in {@code fullRows} (ascending board-row
     * indices, pre-compaction) are cleared. Mutates {@code b.activePieces[*].location} and
     * {@code b.fallingColumns[*].bottomY} in place.
     *
     * <p>Pieces that are spawn-blocked, or that already overlap a solid tile before any
     * clearing happens, are left untouched — moving them would violate the invariant the
     * algorithm relies on. Other players' pieces below a pushed piece are chain-pushed if the
     * push would otherwise make them overlap.
     */
    static void pushPiecesUnderOverhangs(Board b, ArrayList<Integer> fullRows) {
        if (fullRows.isEmpty()) return;
        PushState state = PushState.begin(b);
        if (state == null) return;

        for (int k = 0; k < fullRows.size(); k++) {
            // fullRows is ascending; k rows below this one have already been removed from
            // the snapshot, so this row's index in the partially-collapsed snapshot is offset.
            collapseRow(b, state.solid, fullRows.get(k) - k);
            state.resolveStep(b, DOWN);
        }
        state.apply(b, DOWN);
    }

    /**
     * Pushes every eligible active piece and falling column up by however much is needed to
     * stay clear of the rising stack as {@code insertedRows} are inserted at the bottom of the
     * board, one row per element in insertion order. Each element holds the tile types of one
     * new bottom row ({@link Tile#EMPTY} for its gap columns), so a piece sitting over a gap is
     * only pushed once the garbage actually reaches it. Mutates
     * {@code b.activePieces[*].location} and {@code b.fallingColumns[*].bottomY} in place.
     *
     * <p>Must be called before the board grid itself is shifted, since it snapshots the
     * pre-insertion occupancy. Pieces above a pushed piece are chain-pushed if the push would
     * otherwise make them overlap.
     */
    static void pushPiecesAboveGarbage(Board b, byte[][] insertedRows) {
        if (insertedRows == null || insertedRows.length == 0) return;
        PushState state = PushState.begin(b);
        if (state == null) return;

        for (byte[] row : insertedRows) {
            raiseRow(b, state.solid, row);
            state.resolveStep(b, UP);
        }
        state.apply(b, UP);
    }

    /**
     * Per-row push accumulator: the evolving occupancy snapshot, which pieces and falling
     * columns may be moved at all, and how far each has been pushed so far.
     */
    private static final class PushState {
        final boolean[][] solid;
        final boolean[] eligible;
        final boolean[] fallEligible;
        final int[] push;
        final int[] fallPush;

        private PushState(boolean[][] solid, boolean[] eligible, boolean[] fallEligible) {
            this.solid = solid;
            this.eligible = eligible;
            this.fallEligible = fallEligible;
            this.push = new int[eligible.length];
            this.fallPush = new int[fallEligible.length];
        }

        /**
         * Snapshots {@code b} and marks what may move. Pieces that are spawn-blocked or that
         * already overlap a solid tile are ineligible. Returns null when there is nothing to
         * push.
         */
        static PushState begin(Board b) {
            int n = b.activePieces.size();
            int fc = b.fallingColumns.size();
            if (n == 0 && fc == 0) return null;

            boolean[][] solid = snapshotSolid(b);

            boolean[] eligible = new boolean[n];
            for (int i = 0; i < n; i++) {
                Piece p = b.activePieces.get(i);
                eligible[i] = !p.isBlockedFromSpawning && !overlapsSolid(b, p, 0, solid);
            }
            boolean[] fallEligible = new boolean[fc];
            for (int i = 0; i < fc; i++) {
                fallEligible[i] = !fallingOverlapsSolid(b.fallingColumns.get(i), 0, solid, b);
            }
            return new PushState(solid, eligible, fallEligible);
        }

        /**
         * Runs the chain-push fixed point for a single row step in direction {@code dir}
         * ({@link #DOWN} or {@link #UP}) against the already-updated snapshot, then commits one
         * cell of movement to everything that has to move.
         */
        void resolveStep(Board b, int dir) {
            int n = eligible.length;
            int fc = fallEligible.length;
            boolean[] moving = new boolean[n];
            boolean[] fallMoving = new boolean[fc];

            boolean changed = true;
            while (changed) {
                changed = false;
                for (int i = 0; i < n; i++) {
                    if (!eligible[i] || moving[i]) continue;
                    Piece p = b.activePieces.get(i);
                    boolean mustMove = overlapsSolid(b, p, dir * push[i], solid);
                    for (int j = 0; j < n && !mustMove; j++) {
                        if (j == i || !eligible[j] || !moving[j]) continue;
                        mustMove = piecesOverlapAtOffsets(b, i, dir * push[i], j, dir * (push[j] + 1));
                    }
                    for (int j = 0; j < fc && !mustMove; j++) {
                        if (!fallEligible[j] || !fallMoving[j]) continue;
                        mustMove = pieceOverlapsFalling(b, i, dir * push[i],
                                b.fallingColumns.get(j), dir * (fallPush[j] + 1));
                    }
                    if (mustMove) {
                        moving[i] = true;
                        changed = true;
                    }
                }
                for (int i = 0; i < fc; i++) {
                    if (!fallEligible[i] || fallMoving[i]) continue;
                    FallingColumn col = b.fallingColumns.get(i);
                    boolean mustMove = fallingOverlapsSolid(col, dir * fallPush[i], solid, b);
                    for (int j = 0; j < n && !mustMove; j++) {
                        if (!eligible[j] || !moving[j]) continue;
                        mustMove = pieceOverlapsFalling(b, j, dir * (push[j] + 1), col, dir * fallPush[i]);
                    }
                    for (int j = 0; j < fc && !mustMove; j++) {
                        if (j == i || !fallEligible[j] || !fallMoving[j]) continue;
                        mustMove = fallingOverlap(col, dir * fallPush[i],
                                b.fallingColumns.get(j), dir * (fallPush[j] + 1));
                    }
                    if (mustMove) {
                        fallMoving[i] = true;
                        changed = true;
                    }
                }
            }

            for (int i = 0; i < n; i++) {
                if (moving[i]) push[i]++;
            }
            for (int i = 0; i < fc; i++) {
                if (fallMoving[i]) fallPush[i]++;
            }
        }

        /** Moves everything that accumulated a push by its total, in direction {@code dir}. */
        void apply(Board b, int dir) {
            for (int i = 0; i < push.length; i++) {
                if (push[i] > 0) {
                    b.activePieces.get(i).location.add(0, -dir * push[i]);
                }
            }
            for (int i = 0; i < fallPush.length; i++) {
                if (fallPush[i] > 0) {
                    FallingColumn col = b.fallingColumns.get(i);
                    col.bottomY -= dir * fallPush[i];
                    // Keep resting at an integer-ish position after a discrete push
                    if (col.isAtIntegerRow() || !col.moving) {
                        col.bottomY = (float) Math.floor(col.bottomY);
                        col.moving = false;
                    }
                }
            }
        }
    }

    /** {@code solid[y][x]} true for a disallowed cell or a non-empty locked tile. */
    private static boolean[][] snapshotSolid(Board b) {
        boolean[][] solid = new boolean[b.height][b.width];
        for (int y = 0; y < b.height; y++) {
            for (int x = 0; x < b.width; x++) {
                solid[y][x] = !b.allowedTiles[y][x] || b.board[y][x].get() != Tile.EMPTY;
            }
        }
        return solid;
    }

    /**
     * Removes row {@code row} from the snapshot and shifts everything above it down by one,
     * mirroring {@link BoardLineClear}'s compaction rule: permanent ({@code allowedTiles=false})
     * cells are never overwritten, and a disallowed source cell reads as empty.
     */
    private static void collapseRow(Board b, boolean[][] solid, int row) {
        for (int y = row; y < b.height - 1; y++) {
            for (int x = 0; x < b.width; x++) {
                if (!b.allowedTiles[y][x]) continue;
                solid[y][x] = b.allowedTiles[y + 1][x] && solid[y + 1][x];
            }
        }
        for (int x = 0; x < b.width; x++) {
            if (b.allowedTiles[b.height - 1][x]) {
                solid[b.height - 1][x] = false;
            }
        }
    }

    /**
     * Shifts the snapshot up by one row and writes {@code rowTypes} into the vacated bottom
     * row, mirroring how {@link Board#spawnGarbageRows} inserts a garbage row: permanent
     * ({@code allowedTiles=false}) cells are never overwritten, a disallowed source cell reads
     * as empty, and content shifted past the top of the board is discarded.
     */
    private static void raiseRow(Board b, boolean[][] solid, byte[] rowTypes) {
        for (int y = b.height - 1; y >= 1; y--) {
            for (int x = 0; x < b.width; x++) {
                if (!b.allowedTiles[y][x]) continue;
                solid[y][x] = b.allowedTiles[y - 1][x] && solid[y - 1][x];
            }
        }
        for (int x = 0; x < b.width; x++) {
            if (!b.allowedTiles[0][x]) continue;
            solid[0][x] = rowTypes != null && x < rowTypes.length && rowTypes[x] != Tile.EMPTY;
        }
    }

    /**
     * Returns true if piece {@code p}, shifted down by {@code downShift} cells from its actual
     * current location, overlaps a solid cell in {@code solid}.
     */
    private static boolean overlapsSolid(Board b, Piece p, int downShift, boolean[][] solid) {
        for (Vector2 offset : p.tiles) {
            int mx = (int) Math.floor(p.location.x + offset.x);
            int my = (int) Math.floor(p.location.y - downShift + offset.y);
            if (mx < 0 || mx >= b.width) return true;
            if (my < 0) return true;
            if (my >= b.height) continue;
            if (solid[my][mx]) return true;
        }
        return false;
    }

    private static boolean fallingOverlapsSolid(FallingColumn col, int downShift,
                                                boolean[][] solid, Board b) {
        int bottom = col.bottomRow() - downShift;
        for (int i = 0; i < col.height(); i++) {
            int y = bottom + i;
            int x = col.x;
            if (x < 0 || x >= b.width) return true;
            if (y < 0) return true;
            if (y >= b.height) continue;
            if (solid[y][x]) return true;
        }
        return false;
    }

    /**
     * Returns true if piece {@code i} (shifted down by {@code downShiftI}) and piece
     * {@code j} (shifted down by {@code downShiftJ}) share any occupied cell.
     */
    private static boolean piecesOverlapAtOffsets(Board b, int i, int downShiftI, int j, int downShiftJ) {
        Piece pi = b.activePieces.get(i);
        Piece pj = b.activePieces.get(j);
        for (Vector2 oi : pi.tiles) {
            int ix = (int) Math.floor(pi.location.x + oi.x);
            int iy = (int) Math.floor(pi.location.y - downShiftI + oi.y);
            for (Vector2 oj : pj.tiles) {
                int jx = (int) Math.floor(pj.location.x + oj.x);
                int jy = (int) Math.floor(pj.location.y - downShiftJ + oj.y);
                if (ix == jx && iy == jy) return true;
            }
        }
        return false;
    }

    private static boolean pieceOverlapsFalling(Board b, int pieceId, int pieceDownShift,
                                                FallingColumn col, int fallDownShift) {
        Piece p = b.activePieces.get(pieceId);
        int fallBottom = col.bottomRow() - fallDownShift;
        int fallTop = fallBottom + col.height() - 1;
        for (Vector2 offset : p.tiles) {
            int mx = (int) Math.floor(p.location.x + offset.x);
            int my = (int) Math.floor(p.location.y - pieceDownShift + offset.y);
            if (mx == col.x && my >= fallBottom && my <= fallTop) return true;
        }
        return false;
    }

    private static boolean fallingOverlap(FallingColumn a, int aShift, FallingColumn b, int bShift) {
        if (a.x != b.x) return false;
        int aBottom = a.bottomRow() - aShift;
        int aTop = aBottom + a.height() - 1;
        int bBottom = b.bottomRow() - bShift;
        int bTop = bBottom + b.height() - 1;
        return aBottom <= bTop && bBottom <= aTop;
    }
}
