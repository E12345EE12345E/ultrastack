package me.ethanchen.game.board;

import java.util.ArrayList;

import com.badlogic.gdx.math.Vector2;

/**
 * Pushes active pieces down when a descending overhang (locked tiles compacting downward
 * during a line clear) would otherwise intersect or phase through them.
 *
 * <p>Relies on an invariant of {@link BoardLineClear}: a row can only be cleared when every
 * allowed cell in it holds a locked tile, so no active piece can ever have a mino inside a
 * cleared row. Since a piece's minos are vertically contiguous, every piece therefore lies
 * entirely above or entirely below each cleared row. Collapsing a single row shifts solid
 * content down by exactly one cell, so a piece that did not overlap solid tiles before that
 * single-row collapse can always be resolved by moving down by exactly one cell — the "crushed
 * between the overhang and the stack" case is unreachable. This is why the fix replays the
 * clear one row at a time (via a boolean occupancy snapshot) rather than resolving the net
 * shift in one shot: a thin overhang must "catch" a piece on each individual row it passes
 * through, not just on the total displacement.
 */
final class BoardPiecePush {

    private BoardPiecePush() {}

    /**
     * Pushes every eligible active piece down by however much is needed to stay clear of
     * descending overhangs as the rows in {@code fullRows} (ascending board-row indices,
     * pre-compaction) are cleared. Mutates {@code b.activePieces[*].location} in place.
     *
     * <p>Pieces that are spawn-blocked, or that already overlap a solid tile before any
     * clearing happens, are left untouched — moving them would violate the invariant the
     * algorithm relies on. Other players' pieces below a pushed piece are chain-pushed if the
     * push would otherwise make them overlap.
     */
    static void pushPiecesUnderOverhangs(Board b, ArrayList<Integer> fullRows) {
        int n = b.activePieces.size();
        if (n == 0 || fullRows.isEmpty()) return;

        boolean[][] solid = snapshotSolid(b);

        boolean[] eligible = new boolean[n];
        for (int i = 0; i < n; i++) {
            Piece p = b.activePieces.get(i);
            eligible[i] = !p.isBlockedFromSpawning && !overlapsSolid(b, p, 0, solid);
        }

        int[] push = new int[n];

        for (int k = 0; k < fullRows.size(); k++) {
            // fullRows is ascending; k rows below this one have already been removed from
            // the snapshot, so this row's index in the partially-collapsed snapshot is offset.
            int row = fullRows.get(k) - k;
            collapseRow(b, solid, row);

            boolean[] moving = new boolean[n];
            boolean changed = true;
            while (changed) {
                changed = false;
                for (int i = 0; i < n; i++) {
                    if (!eligible[i] || moving[i]) continue;
                    Piece p = b.activePieces.get(i);
                    // Check the piece's current (not-yet-incremented) shifted position against
                    // the grid as collapsed so far this row; see class doc for why one row of
                    // collapse can force at most one additional step of push per piece.
                    boolean mustMove = overlapsSolid(b, p, push[i], solid);
                    if (!mustMove) {
                        for (int j = 0; j < n && !mustMove; j++) {
                            if (j == i || !eligible[j] || !moving[j]) continue;
                            if (piecesOverlapAtOffsets(b, i, push[i], j, push[j] + 1)) {
                                mustMove = true;
                            }
                        }
                    }
                    if (mustMove) {
                        moving[i] = true;
                        changed = true;
                    }
                }
            }

            for (int i = 0; i < n; i++) {
                if (moving[i]) push[i]++;
            }
        }

        for (int i = 0; i < n; i++) {
            if (push[i] > 0) {
                b.activePieces.get(i).location.add(0, -push[i]);
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
}
