package me.ethanchen.game.board;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

import com.badlogic.gdx.math.Vector2;

import me.ethanchen.game.GameConstants;

/**
 * Falling-block trigger, motion, merge, hover, and landing logic.
 * Package-private — callers outside {@code game.board} go through the {@link Board} API.
 */
final class BoardFallingBlocks {

    private BoardFallingBlocks() {}

    // -------------------------------------------------------------------------
    // Trigger (on fall-triggering piece lock)
    // -------------------------------------------------------------------------

    /**
     * Converts placed cells of a fall-triggering piece into falling columns. Must run after
     * minoes are written to the board and before {@link BoardLineClear#clearAndSettle}, so
     * converted cells read as empty for the subsequent line-clear check.
     */
    static void triggerFromLock(Board b, LineClearResult result) {
        if (result.placedCells == null || result.placedCells.isEmpty()) return;

        // Group placed cells by column: list of Y values per X
        HashMap<Integer, ArrayList<Integer>> byCol = new HashMap<>();
        for (int[] cell : result.placedCells) {
            byCol.computeIfAbsent(cell[0], k -> new ArrayList<>()).add(cell[1]);
        }

        for (var entry : byCol.entrySet()) {
            int x = entry.getKey();
            ArrayList<Integer> ys = entry.getValue();
            ys.sort(Integer::compareTo);
            int lowY = ys.get(0);
            int topMinoY = ys.get(ys.size() - 1);

            if (isSolidSupportBelow(b, x, lowY)) {
                tryGroundedColumn(b, result.playerId, x, lowY, topMinoY);
            } else {
                splitAirborneRuns(b, result.playerId, x, ys);
            }
        }
    }

    /**
     * True when the cell directly below {@code (x, y)} is floor, a disallowed cell, or a
     * non-empty locked tile. Active pieces and falling columns do not count.
     */
    private static boolean isSolidSupportBelow(Board b, int x, int y) {
        int below = y - 1;
        if (below < 0) return true;
        if (x < 0 || x >= b.width || below >= b.height) return true;
        if (!b.allowedTiles[below][x]) return true;
        return b.board[below][x].get() != Tile.EMPTY;
    }

    /**
     * Scans downward from the grounded mino. On success, detaches the contiguous solid run
     * from the empty cell up through {@code topMinoY}. On abort (floor / disallowed), leaves
     * the column untouched.
     */
    private static void tryGroundedColumn(Board b, int playerId, int x, int lowY, int topMinoY) {
        int emptyY = -1;
        for (int y = lowY - 1; y >= 0; y--) {
            if (!b.allowedTiles[y][x]) {
                // Abort: disallowed solid — leave tiles untouched
                return;
            }
            if (b.board[y][x].get() == Tile.EMPTY) {
                // Empty (including cells occupied only by another falling column) stops the scan
                emptyY = y;
                break;
            }
        }
        if (emptyY < 0) {
            // Hit the floor without finding an empty cell — abort
            return;
        }

        int bottom = emptyY + 1;
        int top = topMinoY;
        if (bottom > top) return;

        detachRun(b, playerId, x, bottom, top, false);
    }

    /**
     * Splits the piece's minoes in column {@code x} into contiguous vertical runs and turns
     * each into a piece-trigger falling column.
     */
    private static void splitAirborneRuns(Board b, int playerId, int x, ArrayList<Integer> sortedYs) {
        int runStart = 0;
        for (int i = 1; i <= sortedYs.size(); i++) {
            boolean end = i == sortedYs.size()
                    || sortedYs.get(i) != sortedYs.get(i - 1) + 1;
            if (end) {
                int bottom = sortedYs.get(runStart);
                int top = sortedYs.get(i - 1);
                detachRun(b, playerId, x, bottom, top, true);
                runStart = i;
            }
        }
    }

    /**
     * Erases board cells {@code [bottom..top]} in column {@code x} and creates a falling
     * column from their types. Cells that are already empty are skipped (should not happen
     * for a well-formed run).
     */
    private static void detachRun(Board b, int playerId, int x, int bottom, int top,
                                  boolean pieceTrigger) {
        int len = top - bottom + 1;
        byte[] types = new byte[len];
        for (int i = 0; i < len; i++) {
            int y = bottom + i;
            types[i] = b.board[y][x].get();
            b.board[y][x].set(Tile.EMPTY, Tile.SINGLE_TILE);
        }
        FallingColumn col = new FallingColumn();
        col.id = b.nextFallingId++;
        col.x = x;
        col.bottomY = bottom;
        col.types = types;
        col.velocity = 0f;
        col.moving = false;
        col.triggerPlayerId = playerId;
        col.pieceTrigger = pieceTrigger;
        b.fallingColumns.add(col);
    }

    // -------------------------------------------------------------------------
    // Per-tick update
    // -------------------------------------------------------------------------

    /**
     * Advances every falling column by {@code deltaMs}. Returns line-clear results produced
     * by landings this tick (may be empty).
     */
    static ArrayList<LineClearResult> update(Board b, int deltaMs) {
        ArrayList<LineClearResult> results = new ArrayList<>();
        if (b.fallingColumns.isEmpty() || deltaMs <= 0) return results;

        float dt = deltaMs / 1000f;

        // Bottom-most first so chains resolve in one pass
        b.fallingColumns.sort(Comparator.comparingDouble(c -> c.bottomY));

        // Iterate by index; landings/merges mutate the list
        int i = 0;
        while (i < b.fallingColumns.size()) {
            FallingColumn col = b.fallingColumns.get(i);
            float remaining = dt;
            boolean removed = false;

            while (remaining > 0f) {
                if (!col.moving) {
                    BelowAction action = inspectBelow(b, col);
                    switch (action) {
                        case LAND:
                            LineClearResult r = land(b, col);
                            if (r != null) results.add(r);
                            removed = true;
                            remaining = 0f;
                            break;
                        case MERGE: {
                            FallingColumn lower = findFallingAt(b, col.x, col.bottomRow() - 1, col);
                            if (lower != null) {
                                mergeIntoLower(b, lower, col);
                                removed = true;
                            }
                            remaining = 0f;
                            break;
                        }
                        case HOVER:
                            col.velocity = 0f;
                            remaining = 0f;
                            break;
                        case FALL:
                            col.moving = true;
                            break;
                    }
                    if (removed || !col.moving) break;
                }

                // Integrate motion toward the next integer row below
                float targetY = (float) Math.floor(col.bottomY) - 1f;
                if (col.isAtIntegerRow()) {
                    targetY = col.bottomY - 1f;
                } else {
                    targetY = (float) Math.floor(col.bottomY);
                }

                col.velocity = Math.min(
                        GameConstants.FALL_TERMINAL_VELOCITY,
                        col.velocity + GameConstants.FALL_ACCELERATION * remaining);
                float dist = col.velocity * remaining;
                float toTarget = col.bottomY - targetY;

                if (dist >= toTarget - 1e-5f) {
                    // Snap to the target row; leftover dt continues at full speed
                    float timeUsed = toTarget / Math.max(col.velocity, 1e-6f);
                    remaining = Math.max(0f, remaining - timeUsed);
                    col.bottomY = targetY;
                    col.moving = false;
                    // Loop continues with leftover remaining (no re-ramp)
                } else {
                    col.bottomY -= dist;
                    remaining = 0f;
                }
            }

            if (removed) {
                // Column was removed (landed or merged); do not advance i
                // (list shifted). Re-sort not required within the same tick for
                // remaining columns processed bottom-first from the original order.
            } else {
                i++;
            }
        }

        return results;
    }

    private enum BelowAction { LAND, MERGE, HOVER, FALL }

    private static BelowAction inspectBelow(Board b, FallingColumn col) {
        int below = col.bottomRow() - 1;
        if (below < 0) return BelowAction.LAND;
        if (below >= b.height || col.x < 0 || col.x >= b.width) return BelowAction.LAND;
        if (!b.allowedTiles[below][col.x]) return BelowAction.LAND;
        if (b.board[below][col.x].get() != Tile.EMPTY) return BelowAction.LAND;

        FallingColumn other = findFallingAt(b, col.x, below, col);
        if (other != null) return BelowAction.MERGE;

        if (isActivePieceAt(b, col.x, below)) return BelowAction.HOVER;

        return BelowAction.FALL;
    }

    private static FallingColumn findFallingAt(Board b, int x, int y, FallingColumn exclude) {
        for (FallingColumn c : b.fallingColumns) {
            if (c == exclude) continue;
            if (c.occupies(x, y)) return c;
        }
        return null;
    }

    private static boolean isActivePieceAt(Board b, int x, int y) {
        for (Piece p : b.activePieces) {
            if (p == null || p.tiles == null) continue;
            for (Vector2 t : p.tiles) {
                int mx = (int) Math.floor(p.location.x + t.x);
                int my = (int) Math.floor(p.location.y + t.y);
                if (mx == x && my == y) return true;
            }
        }
        return false;
    }

    /**
     * Merges {@code upper} into {@code lower}. Lower keeps triggerPlayerId and bottomY;
     * velocity is max of both; pieceTrigger is OR'd; types are concatenated (lower first).
     * Removes {@code upper} from the board list.
     */
    private static void mergeIntoLower(Board b, FallingColumn lower, FallingColumn upper) {
        byte[] merged = new byte[lower.types.length + upper.types.length];
        System.arraycopy(lower.types, 0, merged, 0, lower.types.length);
        System.arraycopy(upper.types, 0, merged, lower.types.length, upper.types.length);
        lower.types = merged;
        lower.velocity = Math.max(lower.velocity, upper.velocity);
        lower.pieceTrigger = lower.pieceTrigger || upper.pieceTrigger;
        // Keep lower.triggerPlayerId and lower.bottomY
        b.fallingColumns.remove(upper);
    }

    // -------------------------------------------------------------------------
    // Landing
    // -------------------------------------------------------------------------

    /**
     * Writes the column into the board at connection state 0, removes it from the falling
     * list, optionally re-triggers a scan for pieceTrigger columns, and otherwise runs a
     * falling line-clear check. Returns a result when a clear check was performed (including
     * zero-line landings); returns null when the column immediately re-detached.
     */
    private static LineClearResult land(Board b, FallingColumn col) {
        int bottom = col.bottomRow();
        // Snap to integer before writing
        col.bottomY = bottom;

        ArrayList<int[]> landedCells = new ArrayList<>();
        for (int i = 0; i < col.types.length; i++) {
            int y = bottom + i;
            if (y < 0 || y >= b.height || col.x < 0 || col.x >= b.width) continue;
            if (!b.allowedTiles[y][col.x]) continue;
            b.board[y][col.x].set(col.types[i], Tile.SINGLE_TILE);
            landedCells.add(new int[]{col.x, y});
        }

        boolean wasPieceTrigger = col.pieceTrigger;
        int playerId = col.triggerPlayerId;
        b.fallingColumns.remove(col);

        if (wasPieceTrigger && !landedCells.isEmpty()) {
            int lowY = landedCells.get(0)[1];
            int topY = landedCells.get(landedCells.size() - 1)[1];
            // Re-run the grounded scan once; on success, skip the clear check for this landing
            int before = b.fallingColumns.size();
            tryGroundedColumn(b, playerId, col.x, lowY, topY);
            // Mark any newly created columns as non-pieceTrigger (flag never propagates)
            for (int i = before; i < b.fallingColumns.size(); i++) {
                b.fallingColumns.get(i).pieceTrigger = false;
            }
            if (b.fallingColumns.size() > before) {
                // Successfully re-detached — no clear check this landing
                return null;
            }
        }

        LineClearResult result = new LineClearResult();
        result.placed = true;
        result.fallingClear = true;
        result.playerId = playerId;
        result.boardIndex = b.getBoardIndex();
        result.pieceType = col.types.length > 0 ? col.types[0] : Tile.EMPTY;
        result.restingX = col.x;
        result.restingY = bottom;
        result.restingCenterX = col.x;
        result.restingCenterY = bottom + (col.types.length - 1) * 0.5f;
        result.placedCells.addAll(landedCells);

        BoardLineClear.clearAndSettle(b, result);
        return result;
    }

    // -------------------------------------------------------------------------
    // Garbage / push helpers
    // -------------------------------------------------------------------------

    /**
     * After garbage fills bottom rows, any falling column intersecting a solid board cell
     * is pushed upward until clear, then left for the next tick to continue.
     */
    static void resolveAfterGarbage(Board b) {
        for (FallingColumn col : b.fallingColumns) {
            while (intersectsSolidBoard(b, col) && col.bottomY + col.height() < b.height + 8) {
                col.bottomY += 1f;
                col.moving = false;
                col.velocity = 0f;
            }
            // Clamp inside board if pushed past the top
            float maxBottom = b.height - col.height();
            if (col.bottomY > maxBottom) {
                col.bottomY = Math.max(0, maxBottom);
            }
        }
    }

    private static boolean intersectsSolidBoard(Board b, FallingColumn col) {
        int bottom = col.bottomRow();
        for (int i = 0; i < col.height(); i++) {
            int y = bottom + i;
            if (y < 0 || y >= b.height || col.x < 0 || col.x >= b.width) continue;
            if (!b.allowedTiles[y][col.x]) return true;
            if (b.board[y][col.x].get() != Tile.EMPTY) return true;
        }
        return false;
    }

    /**
     * Soft-reconciles local falling columns against a network snapshot.
     * Matching ids keep local sub-tile progress when the discrete row agrees.
     */
    static void reconcileFromNet(Board b, me.ethanchen.network.dto.NetFallingColumn[] net) {
        if (net == null) {
            b.fallingColumns.clear();
            return;
        }

        HashMap<Integer, FallingColumn> localById = new HashMap<>();
        for (FallingColumn c : b.fallingColumns) {
            localById.put(c.id, c);
        }

        ArrayList<FallingColumn> next = new ArrayList<>(net.length);
        int maxId = b.nextFallingId - 1;
        for (me.ethanchen.network.dto.NetFallingColumn n : net) {
            if (n == null) continue;
            FallingColumn local = localById.get(n.id);
            FallingColumn col;
            if (local != null) {
                col = local;
                col.types = n.types != null ? Arrays.copyOf(n.types, n.types.length) : new byte[0];
                col.velocity = n.velocity;
                col.moving = n.moving;
                col.triggerPlayerId = n.triggerPlayerId;
                col.pieceTrigger = n.pieceTrigger;
                col.x = n.x;
                if ((int) Math.floor(n.bottomY) == (int) Math.floor(local.bottomY)) {
                    // Soft: keep local sub-tile progress
                } else {
                    col.bottomY = n.bottomY;
                }
            } else {
                col = fromNet(n);
            }
            next.add(col);
            if (n.id > maxId) maxId = n.id;
        }
        b.fallingColumns.clear();
        b.fallingColumns.addAll(next);
        b.nextFallingId = maxId + 1;
    }

    static FallingColumn fromNet(me.ethanchen.network.dto.NetFallingColumn n) {
        FallingColumn col = new FallingColumn();
        col.id = n.id;
        col.x = n.x;
        col.bottomY = n.bottomY;
        col.types = n.types != null ? Arrays.copyOf(n.types, n.types.length) : new byte[0];
        col.velocity = n.velocity;
        col.moving = n.moving;
        col.triggerPlayerId = n.triggerPlayerId;
        col.pieceTrigger = n.pieceTrigger;
        return col;
    }

    static me.ethanchen.network.dto.NetFallingColumn toNet(FallingColumn c) {
        me.ethanchen.network.dto.NetFallingColumn n = new me.ethanchen.network.dto.NetFallingColumn();
        n.id = c.id;
        n.x = c.x;
        n.bottomY = c.bottomY;
        n.types = c.types != null ? Arrays.copyOf(c.types, c.types.length) : new byte[0];
        n.velocity = c.velocity;
        n.moving = c.moving;
        n.triggerPlayerId = c.triggerPlayerId;
        n.pieceTrigger = c.pieceTrigger;
        return n;
    }
}
