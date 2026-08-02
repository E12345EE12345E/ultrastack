package me.ethanchen.game.board;

import com.badlogic.gdx.math.Vector2;

/**
 * Static collision-detection utilities extracted from {@link Board}.
 *
 * <p>All methods accept a {@link Board} reference and operate on its protected fields.
 * They are package-private because they are an implementation detail of the board simulation;
 * callers outside the {@code game.board} package should go through the {@link Board} API.
 */
final class BoardCollision {

    private BoardCollision() {}

    // -------------------------------------------------------------------------
    // Move / placement checks
    // -------------------------------------------------------------------------

    /** Returns true if piece {@code id} can shift by ({@code xdiff}, {@code ydiff}). */
    static boolean canMovePiece(Board b, int id, int xdiff, int ydiff) {
        if (id < 0 || id >= b.activePieces.size()) return false;
        Piece p = b.activePieces.get(id);
        for (int i = 0; i < p.tiles.length; i++) {
            float lx = p.location.x + p.tiles[i].x + xdiff;
            float ly = p.location.y + p.tiles[i].y + ydiff;
            if (lx < 0 || ly < 0 || lx >= b.width || ly >= b.height) return false;
            int ix = (int) lx, iy = (int) ly;
            if (b.board[iy][ix] == null || b.board[iy][ix].get() != 0) return false;
            if (!b.allowedTiles[iy][ix]) return false;
            for (int j = 0; j < b.activePieces.size(); j++) {
                if (j == id) continue;
                Piece other = b.activePieces.get(j);
                if (p.justSpawned || other.justSpawned) continue;
                for (Vector2 t : other.tiles) {
                    if (lx == t.x + other.location.x
                            && ly == t.y + other.location.y) return false;
                }
            }
        }
        return true;
    }

    /**
     * Checks whether piece {@code id} can occupy anchor ({@code baseX}, {@code baseY}) without
     * mutating any state. Mirrors {@link #canMovePiece} exactly, used for shadow projection.
     */
    static boolean canPieceBeAt(Board b, int id, float baseX, float baseY) {
        Piece p = b.activePieces.get(id);
        for (int i = 0; i < p.tiles.length; i++) {
            float lx = baseX + p.tiles[i].x;
            float ly = baseY + p.tiles[i].y;
            if (lx < 0 || ly < 0 || lx >= b.width || ly >= b.height) return false;
            int ix = (int) lx, iy = (int) ly;
            if (b.board[iy][ix] == null || b.board[iy][ix].get() != 0) return false;
            if (!b.allowedTiles[iy][ix]) return false;
            for (int j = 0; j < b.activePieces.size(); j++) {
                if (j == id) continue;
                Piece other = b.activePieces.get(j);
                if (p.justSpawned || other.justSpawned) continue;
                for (Vector2 t : other.tiles) {
                    if (lx == t.x + other.location.x
                            && ly == t.y + other.location.y) return false;
                }
            }
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Solid-support checks (used for locking, shadow, and lock timers)
    // -------------------------------------------------------------------------

    /**
     * Returns true if piece {@code p} at anchor ({@code x}, {@code y}) has solid support
     * directly below at least one mino (floor, disallowed cell, or non-empty board tile).
     * Other active pieces are NOT counted.
     */
    static boolean hasSolidSupportAt(Board b, Piece p, float x, float y) {
        for (Vector2 offset : p.tiles) {
            int mx = (int) Math.floor(x + offset.x);
            int my = (int) Math.floor(y + offset.y);
            int below = my - 1;
            if (below < 0) return true;
            if (mx >= 0 && mx < b.width && below < b.height) {
                if (!b.allowedTiles[below][mx]) return true;
                if (b.board[below][mx].get() != Tile.EMPTY) return true;
            }
        }
        return false;
    }

    /** Convenience overload that reads the piece's current location. */
    static boolean hasSolidSupportNow(Board b, int id) {
        Piece p = b.activePieces.get(id);
        return hasSolidSupportAt(b, p, p.location.x, p.location.y);
    }

    /**
     * Returns true if ({@code x}, {@code y}) is out of bounds, a disallowed cell, or a
     * non-empty board tile.
     */
    static boolean isSolid(Board b, int x, int y) {
        if (x < 0 || x >= b.width || y < 0 || y >= b.height) return true;
        if (!b.allowedTiles[y][x]) return true;
        return b.board[y][x].get() != Tile.EMPTY;
    }

    // -------------------------------------------------------------------------
    // justSpawned overlap detection
    // -------------------------------------------------------------------------

    /**
     * Returns true if piece {@code id}'s tiles currently overlap any tile of another active
     * piece, regardless of {@code justSpawned} state. Used to decide when a piece's
     * {@code justSpawned} grace flag should be cleared.
     */
    static boolean overlapsAnyOtherPiece(Board b, int id) {
        Piece p = b.activePieces.get(id);
        for (int i = 0; i < p.tiles.length; i++) {
            float lx = p.location.x + p.tiles[i].x;
            float ly = p.location.y + p.tiles[i].y;
            for (int j = 0; j < b.activePieces.size(); j++) {
                if (j == id) continue;
                Piece other = b.activePieces.get(j);
                for (Vector2 t : other.tiles) {
                    if (lx == t.x + other.location.x && ly == t.y + other.location.y) return true;
                }
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Lateral-blocker detection (bump events)
    // -------------------------------------------------------------------------

    /**
     * Returns the id of the single other player whose active piece is the sole reason piece
     * {@code id} cannot move laterally by {@code xdiff} columns.  Returns -1 if the move is
     * blocked by a wall, the board floor/ceiling, any locked tile, a disallowed cell, or if
     * more than one other player contributes to the blockage.
     */
    static int getLateralBlocker(Board b, int id, int xdiff) {
        if (id < 0 || id >= b.activePieces.size()) return -1;
        Piece p = b.activePieces.get(id);
        int blockerId = -1;
        for (int i = 0; i < p.tiles.length; i++) {
            float lx = p.location.x + p.tiles[i].x + xdiff;
            float ly = p.location.y + p.tiles[i].y;
            // Any wall/floor/ceiling/locked-tile involvement means it is NOT a pure player bump
            if (lx < 0 || lx >= b.width || ly < 0 || ly >= b.height) return -1;
            int ix = (int) lx, iy = (int) ly;
            if (!b.allowedTiles[iy][ix]) return -1;
            if (b.board[iy][ix] != null && b.board[iy][ix].get() != 0) return -1;
            for (int j = 0; j < b.activePieces.size(); j++) {
                if (j == id) continue;
                Piece other = b.activePieces.get(j);
                if (p.justSpawned || other.justSpawned) continue;
                for (Vector2 t : other.tiles) {
                    if (lx == t.x + other.location.x && ly == t.y + other.location.y) {
                        if (blockerId == -1) {
                            blockerId = j;
                        } else if (blockerId != j) {
                            return -1; // two different players involved
                        }
                    }
                }
            }
        }
        return blockerId;
    }

    // -------------------------------------------------------------------------
    // Resting-blocker detection
    // -------------------------------------------------------------------------

    /**
     * Scans cells directly below each mino of piece {@code id} and returns the single other
     * active piece responsible for supporting it, or {@code -1} if no single blocker can be
     * identified (floor, multiple blockers, etc.).
     */
    static int findRestingBlocker(Board b, int id) {
        Piece p = b.activePieces.get(id);
        int blockerId = -1;
        for (Vector2 offset : p.tiles) {
            int mx = (int) Math.floor(p.location.x + offset.x);
            int my = (int) Math.floor(p.location.y + offset.y);
            int below = my - 1;
            if (below < 0) return -1;
            for (int j = 0; j < b.activePieces.size(); j++) {
                if (j == id) continue;
                Piece other = b.activePieces.get(j);
                if (p.justSpawned || other.justSpawned) continue;
                for (Vector2 t : other.tiles) {
                    if (mx == (int)(t.x + other.location.x) && below == (int)(t.y + other.location.y)) {
                        if (blockerId == -1) {
                            blockerId = j;
                        } else if (blockerId != j) {
                            return -1;
                        }
                    }
                }
            }
        }
        return blockerId;
    }

    // -------------------------------------------------------------------------
    // Kick tables (SRS / SRS+)
    // -------------------------------------------------------------------------

    /** Returns the SRS kick table for the given piece type, or null if no kicks apply. */
    static Vector2[] kickTableFor(byte type) {
        if (type == Piece.I) return Piece.WALL_KICKS_I;
        if (type == Piece.J || type == Piece.L || type == Piece.S
                || type == Piece.T || type == Piece.Z) return Piece.WALL_KICKS_JLSTZ;
        if (type == Piece.I3) return Piece.WALL_KICKS_I3;
        if (type == Piece.L3) return Piece.WALL_KICKS_L3;
        return null;
    }

    /** Returns the SRS+ 180-rotation kick table for the given piece type, or null if none. */
    static Vector2[] kickTable180For(byte type) {
        if (type == Piece.I) return Piece.WALL_KICKS_180_I;
        if (type == Piece.J || type == Piece.L || type == Piece.S
                || type == Piece.T || type == Piece.Z) return Piece.WALL_KICKS_180_JLSTZ;
        if (type == Piece.I3) return Piece.WALL_KICKS_180_I3;
        if (type == Piece.L3) return Piece.WALL_KICKS_180_L3;
        return null;
    }

    /**
     * Tries kick tests 1–4 for the given kick table row (test 0 at (0,0) is already tried).
     * Applies the offset and returns true on the first passing test.
     */
    static boolean tryKicks(Board b, int id, int row, Vector2[] kickTable) {
        int base = row * 5;
        for (int i = 1; i < 5; i++) {
            Vector2 k = kickTable[base + i];
            if (canMovePiece(b, id, (int) k.x, (int) k.y)) {
                b.activePieces.get(id).location.add(k.x, k.y);
                return true;
            }
        }
        return false;
    }

    /**
     * Tries up to {@code stride} 180-rotation kicks starting at {@code fromRotation * stride}.
     * Unlike {@link #tryKicks}, all entries are real offsets (no implicit (0,0) test 0).
     */
    static boolean tryKicks180(Board b, int id, int fromRotation, Vector2[] kickTable, int stride) {
        int base = fromRotation * stride;
        for (int i = 0; i < stride; i++) {
            Vector2 k = kickTable[base + i];
            if (canMovePiece(b, id, (int) k.x, (int) k.y)) {
                b.activePieces.get(id).location.add(k.x, k.y);
                return true;
            }
        }
        return false;
    }
}
