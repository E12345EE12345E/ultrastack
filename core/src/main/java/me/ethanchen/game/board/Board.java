package me.ethanchen.game.board;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import com.badlogic.gdx.math.Vector2;

public class Board {
    protected final boolean[][] allowedTiles;
    protected final Tile[][] board;
    protected final byte width;
    protected final byte height;
    protected final Vector2[] spawnPositions;
    protected final PieceQueue[] pieceQueues;
    protected final ArrayList<Piece> activePieces;

    // Hold state (shared across all players on this board)
    private byte heldPieceType = 0;   // 0 = empty
    private boolean[] playerHoldUsed; // indexed by player; true until that player hard drops

    public boolean[][] getAllowedTiles() { return allowedTiles; }
    public Tile[][] getBoard() { return board; }
    public int bw() { return width; }
    public int bh() { return height; }
    public Vector2[] getSpawnPositions() { return spawnPositions; }
    public Vector2 getSpawnPos(int p) { return spawnPositions[p]; }
    public PieceQueue[] getPieceQueues() { return pieceQueues; }
    public PieceQueue getPieceQueue(int p) { return pieceQueues[p]; }
    public ArrayList<Piece> getActivePieces() { return activePieces; }
    public Piece getActivePiece(int p) { return activePieces.get(p); }
    public byte getHeldPieceType() { return heldPieceType; }
    public void setHeldPieceType(byte type) { heldPieceType = type; }
    public boolean isPlayerHoldUsed(int id) {
        return playerHoldUsed != null && id >= 0 && id < playerHoldUsed.length && playerHoldUsed[id];
    }

    // Init

    public Board(Presets preset) {
        Random r = new Random();
        switch (preset) {
            default:
            case STANDARD_SINGLE:
                width = 10;
                height = 24;
                allowedTiles = new boolean[height][width];
                for (boolean[] row : allowedTiles) Arrays.fill(row, true);
                board = emptyBoard();
                spawnPositions = new Vector2[]{new Vector2(4, 20)};
                pieceQueues = new PieceQueue[]{new PieceQueue(r.nextInt(), PieceQueue.BagTypes.BAG_7)};
                activePieces = new ArrayList<Piece>();
                assert(spawnPositions.length == 1);
                return;
            case STANDARD_DUO:
                width = 10;
                height = 24;
                allowedTiles = new boolean[height][width];
                for (boolean[] row : allowedTiles) Arrays.fill(row, true);
                for (int i=21; i<24; i++) {
                    allowedTiles[i][4] = false;
                    allowedTiles[i][5] = false;
                }
                board = emptyBoard();
                spawnPositions = new Vector2[]{new Vector2(1, 20), new Vector2(7, 20)};
                pieceQueues = new PieceQueue[]{new PieceQueue(r.nextInt(), PieceQueue.BagTypes.BAG_7), new PieceQueue(r.nextInt(), PieceQueue.BagTypes.BAG_7)};
                activePieces = new ArrayList<Piece>();
                assert(spawnPositions.length == 2);
                return;
            case STANDARD_TRIO:
                width = 16;
                height = 24;
                allowedTiles = new boolean[height][width];
                for (boolean[] row : allowedTiles) Arrays.fill(row, true);
                for (int i=21; i<24; i++) {
                    allowedTiles[i][4] = false;
                    allowedTiles[i][5] = false;
                    allowedTiles[i][10] = false;
                    allowedTiles[i][11] = false;
                }
                board = emptyBoard();
                spawnPositions = new Vector2[]{new Vector2(1, 20), new Vector2(7, 20), new Vector2(13, 20)};
                pieceQueues = new PieceQueue[]{new PieceQueue(r.nextInt(), PieceQueue.BagTypes.BAG_7), new PieceQueue(r.nextInt(), PieceQueue.BagTypes.BAG_7), new PieceQueue(r.nextInt(), PieceQueue.BagTypes.BAG_7)};
                activePieces = new ArrayList<Piece>();
                assert(spawnPositions.length == 3);
                return;
            case STANDARD_4P:
                width = 22;
                height = 24;
                allowedTiles = new boolean[height][width];
                for (boolean[] row : allowedTiles) Arrays.fill(row, true);
                for (int i=21; i<24; i++) {
                    allowedTiles[i][4] = false;
                    allowedTiles[i][5] = false;
                    allowedTiles[i][10] = false;
                    allowedTiles[i][11] = false;
                    allowedTiles[i][16] = false;
                    allowedTiles[i][17] = false;
                }
                board = emptyBoard();
                spawnPositions = new Vector2[]{new Vector2(1, 20), new Vector2(7, 20), new Vector2(13, 20), new Vector2(19, 20)};
                pieceQueues = new PieceQueue[]{new PieceQueue(r.nextInt(), PieceQueue.BagTypes.BAG_7), new PieceQueue(r.nextInt(), PieceQueue.BagTypes.BAG_7), new PieceQueue(r.nextInt(), PieceQueue.BagTypes.BAG_7), new PieceQueue(r.nextInt(), PieceQueue.BagTypes.BAG_7)};
                activePieces = new ArrayList<Piece>();
                assert(spawnPositions.length == 4);
                return;
            case TEST:
                width = 20;
                height = 24;
                allowedTiles = new boolean[height][width];
                for (boolean[] row : allowedTiles) Arrays.fill(row, true);
                allowedTiles[23][10] = false;
                allowedTiles[23][11] = false;
                allowedTiles[22][10] = false;
                allowedTiles[22][11] = false;
                board = emptyBoard();
                board[21][10].set((byte)1);
                spawnPositions = new Vector2[]{};
                pieceQueues = new PieceQueue[]{};
                activePieces = new ArrayList<Piece>();
                return;
        }
    }

    public Board(NetBoardFull nb) {
        width = nb.width;
        height = nb.height;
        allowedTiles = new boolean[height][width];
        for (int y=0; y<height; y++) {
            for (int x=0; x<width; x++) {
                allowedTiles[y][x] = nb.allowedtiles[y*width + x];
            }
        }
        board = emptyBoard();
        spawnPositions = new Vector2[nb.spawnposx.length];
        for (int i=0; i<spawnPositions.length; i++) {
            spawnPositions[i] = new Vector2(nb.spawnposx[i], nb.spawnposy[i]);
        }
        pieceQueues = new PieceQueue[nb.queues.length];
        for (int i=0; i<pieceQueues.length; i++) {
            pieceQueues[i] = PieceQueue.createFromNetQueue(nb.queues[i]);
        }
        activePieces = new ArrayList<Piece>();
        updateFromNetBoardLight(lightNetBoardFrom(nb));
    }

    public void spawnInitialPieces() {
        if (activePieces.size() > 0) return;
        for (PieceQueue q : pieceQueues) {
            q.refill();
        }
        for (int i = 0; i < spawnPositions.length; i++) {
            Piece piece = Piece.defaultPiece(pieceQueues[i].takeNext());
            piece.location.add(spawnPositions[i]);
            piece.isBlockedFromSpawning = isSpawnBlocked(piece);
            activePieces.add(piece);
        }
    }

    private Tile[][] emptyBoard() {
        Tile[][] retval = new Tile[height][width];
        for (int y=0; y<retval.length; y++) {
            for (int x=0; x<retval[y].length; x++) {
                retval[y][x] = new Tile(0, 0);
            }
        }
        return retval;
    }

    // Piece

    public boolean canMovePiece(int id, int xdiff, int ydiff) {
        if (id > activePieces.size()) return false;
        Piece p = activePieces.get(id);
        ArrayList<Vector2> checkLocs = new ArrayList<Vector2>();
        for (int i=0; i<p.tiles.length; i++) {
            checkLocs.add(new Vector2(p.location.x + p.tiles[i].x + xdiff, p.location.y + p.tiles[i].y + ydiff));
        }
        for (Vector2 loc : checkLocs) {
            // bounds check
            if (loc.x < 0 || loc.y < 0 || loc.x >= width || loc.y >= height) return false;
            // board check
            if (board[(int) loc.y][(int) loc.x] == null || board[(int) loc.y][(int) loc.x].get() != 0) return false;
            if (!allowedTiles[(int) loc.y][(int) loc.x]) return false;
            // active pieces check
            for (int i=0; i<activePieces.size(); i++) {
                if (i==id) continue;
                for (Vector2 t : activePieces.get(i).tiles) if (loc.x == t.x + activePieces.get(i).location.x && loc.y == t.y + activePieces.get(i).location.y) return false;
            }
        }
        return true;
    }

    public boolean moveLeft(int id) {
        if (canMovePiece(id, -1, 0)) {
            Piece p = activePieces.get(id);
            p.location.add(-1, 0);
            p.lastMoveWasRotation = false;
            return true;
        }
        return false;
    }

    public boolean moveRight(int id) {
        if (canMovePiece(id, 1, 0)) {
            Piece p = activePieces.get(id);
            p.location.add(1, 0);
            p.lastMoveWasRotation = false;
            return true;
        }
        return false;
    }

    public boolean moveDown(int id) {
        if (canMovePiece(id, 0, -1)) {
            Piece p = activePieces.get(id);
            p.location.add(0, -1);
            p.lastMoveWasRotation = false;
            return true;
        }
        return false;
    }

    public boolean rotateCW(int id) {
        Piece p = activePieces.get(id);
        byte fromRotation = p.rotation;
        p.rotateCW();
        if (canMovePiece(id, 0, 0)) {
            p.rotateTexCW();
            p.lastMoveWasRotation = true;
            return true;
        }
        Vector2[] kicks = kickTableFor(p.type);
        if (kicks != null && tryKicks(id, fromRotation * 2, kicks)) {
            p.rotateTexCW();
            p.lastMoveWasRotation = true;
            return true;
        }
        p.rotateCCW();
        return false;
    }

    public boolean rotateCCW(int id) {
        Piece p = activePieces.get(id);
        byte fromRotation = p.rotation;
        p.rotateCCW();
        if (canMovePiece(id, 0, 0)) {
            p.rotateTexCCW();
            p.lastMoveWasRotation = true;
            return true;
        }
        Vector2[] kicks = kickTableFor(p.type);
        int row = (fromRotation == 0) ? 7 : fromRotation * 2 - 1;
        if (kicks != null && tryKicks(id, row, kicks)) {
            p.rotateTexCCW();
            p.lastMoveWasRotation = true;
            return true;
        }
        p.rotateCW();
        return false;
    }

    /** Returns the SRS kick table for the given piece type, or null if no kicks apply. */
    private static Vector2[] kickTableFor(byte type) {
        if (type == Piece.I) return Piece.WALL_KICKS_I;
        if (type == Piece.J || type == Piece.L || type == Piece.S
                || type == Piece.T || type == Piece.Z) return Piece.WALL_KICKS_JLSTZ;
        return null;
    }

    /** Returns the SRS+ 180-rotation kick table for the given piece type, or null if none. */
    private static Vector2[] kickTable180For(byte type) {
        if (type == Piece.I) return Piece.WALL_KICKS_180_I;
        if (type == Piece.J || type == Piece.L || type == Piece.S
                || type == Piece.T || type == Piece.Z) return Piece.WALL_KICKS_180_JLSTZ;
        return null;
    }

    /**
     * Tries kick tests 1–4 for the given kick table row (test 0 is (0,0), already tried).
     * Applies the offset and returns true on the first passing test.
     */
    private boolean tryKicks(int id, int row, Vector2[] kickTable) {
        int base = row * 5;
        for (int i = 1; i < 5; i++) {
            Vector2 k = kickTable[base + i];
            if (canMovePiece(id, (int) k.x, (int) k.y)) {
                activePieces.get(id).location.add(k.x, k.y);
                return true;
            }
        }
        return false;
    }

    /**
     * Tries up to {@code stride} 180-rotation kicks from the table, starting at
     * {@code fromRotation * stride}.  Unlike {@link #tryKicks}, all entries are real
     * offsets — there is no implicit (0,0) test 0.
     */
    private boolean tryKicks180(int id, int fromRotation, Vector2[] kickTable, int stride) {
        int base = fromRotation * stride;
        for (int i = 0; i < stride; i++) {
            Vector2 k = kickTable[base + i];
            if (canMovePiece(id, (int) k.x, (int) k.y)) {
                activePieces.get(id).location.add(k.x, k.y);
                return true;
            }
        }
        return false;
    }

    public boolean useHold(int playerId) {
        if (playerId < 0 || playerId >= activePieces.size()) return false;
        // Lazy-init the per-player lock array
        if (playerHoldUsed == null) playerHoldUsed = new boolean[spawnPositions.length];
        if (playerId < playerHoldUsed.length && playerHoldUsed[playerId]) return false; // personal lock
        Piece current = activePieces.get(playerId);
        byte currentType = current.type;
        byte oldHeld = heldPieceType;
        heldPieceType = currentType;
        if (oldHeld == 0) {
            // Hold slot was empty: advance the queue to give player a new piece
            spawnNextPiece(playerId);
        } else {
            // Swap current piece out, spawn the previously held piece
            Piece newPiece = Piece.defaultPiece(oldHeld);
            newPiece.location.add(spawnPositions[playerId]);
            newPiece.isBlockedFromSpawning = isSpawnBlocked(newPiece);
            activePieces.set(playerId, newPiece);
        }
        if (playerId < playerHoldUsed.length) playerHoldUsed[playerId] = true;
        return true;
    }

    public boolean rotate180(int id) {
        Piece p = activePieces.get(id);
        byte fromRotation = p.rotation;
        p.rotate180();
        if (canMovePiece(id, 0, 0)) {
            p.rotateTexCW(); p.rotateTexCW();
            p.lastMoveWasRotation = true;
            return true;
        }
        Vector2[] kicks180 = kickTable180For(p.type);
        if (kicks180 != null) {
            int stride = (p.type == Piece.I) ? 1 : 5;
            if (tryKicks180(id, fromRotation, kicks180, stride)) {
                p.rotateTexCW(); p.rotateTexCW();
                p.lastMoveWasRotation = true;
                return true;
            }
        }
        p.rotate180(); // undo: two 180s = 360
        return false;
    }

    public boolean applyMove(int pieceId, MoveType t) {
        if (pieceId < 0 || pieceId >= activePieces.size()) return false;
        // Blocked pieces may not be moved, rotated, or hard-dropped by normal input.
        // HOLD while blocked is handled server-side via spawnHeldPiece, not here.
        if (activePieces.get(pieceId).isBlockedFromSpawning) return false;
        switch (t) {
            case LEFT: return moveLeft(pieceId);
            case RIGHT: return moveRight(pieceId);
            case SOFT_DROP: return moveDown(pieceId);
            case ROTATE_CW: return rotateCW(pieceId);
            case ROTATE_CCW: return rotateCCW(pieceId);
            case ROTATE_180: return rotate180(pieceId);
            case HOLD: return useHold(pieceId);
            case HARD_DROP: return hardDrop(pieceId) != null;
        }
        return false;
    }

    public void doGravityTick() {
        for (int i=0; i<activePieces.size(); i++) {
            if (activePieces.get(i).isBlockedFromSpawning) continue;
            moveDown(i);
        }
    }

    private boolean isSolid(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return true;
        if (!allowedTiles[y][x]) return true;
        return board[y][x].get() != Tile.EMPTY;
    }

    private static int[] rotateOffset(int x, int y, int r) {
        for (int i = 0; i < r; i++) { int t = x; x = y; y = -t; }
        return new int[]{x, y};
    }

    /**
     * Hard-drops piece {@code id}: slides it down until blocked, then decides whether
     * to lock it.  Returns a {@link LineClearResult} describing everything that happened,
     * or {@code null} if {@code id} is out of range.
     *
     * Placement rule: the piece is locked only when at least one mino is directly
     * supported by the floor, a non-empty board tile, or an {@code allowedTiles=false}
     * cell.  If it comes to rest purely on top of another active piece it is left there
     * (not locked) and {@code result.placed == false}.
     */
    public LineClearResult hardDrop(int id) {
        if (id < 0 || id >= activePieces.size()) return null;
        if (activePieces.get(id).isBlockedFromSpawning) return null;

        // Slide down
        int dropDistance = 0;
        while (canMovePiece(id, 0, -1)) {
            activePieces.get(id).location.add(0, -1);
            dropDistance++;
        }

        Piece p = activePieces.get(id);
        LineClearResult result = new LineClearResult();
        result.playerId = id;
        result.pieceType = p.type;
        result.restingX = (int) Math.floor(p.location.x);
        result.restingY = (int) Math.floor(p.location.y);
        result.restingCenterX = p.location.x;
        result.restingCenterY = p.location.y;
        result.pieceRotation = p.rotation;

        // Check for solid support (floor, board tile, or disallowed cell below each mino)
        boolean hasSolidSupport = false;
        for (Vector2 offset : p.tiles) {
            int mx = (int) Math.floor(p.location.x + offset.x);
            int my = (int) Math.floor(p.location.y + offset.y);
            int below = my - 1;
            if (below < 0) { hasSolidSupport = true; break; }
            if (mx >= 0 && mx < width && below >= 0 && below < height) {
                if (!allowedTiles[below][mx]) { hasSolidSupport = true; break; }
                if (board[below][mx].get() != Tile.EMPTY) { hasSolidSupport = true; break; }
            }
        }

        if (!hasSolidSupport) {
            result.placed = false;
            return result;
        }

        // Lock each mino
        result.placed = true;
        if (playerHoldUsed != null && id < playerHoldUsed.length) playerHoldUsed[id] = false;

        // Spin detection: only applies when the piece was spun directly into place
        SpinType spinType = SpinType.NONE;
        if (dropDistance == 0 && p.lastMoveWasRotation) {
            int px = (int) Math.floor(p.location.x);
            int py = (int) Math.floor(p.location.y);
            if (p.type == Piece.T) {
                // Corner offsets are defined at rotation 0, then rotated by p.rotation.
                // Back corners (behind the T stem): (-1,-1) and (1,-1)
                // Front corners (in front of the T stem): (-1,1) and (1,1)
                int[] b1 = rotateOffset(-1, -1, p.rotation), b2 = rotateOffset(1, -1, p.rotation);
                int[] f1 = rotateOffset(-1,  1, p.rotation), f2 = rotateOffset(1,  1, p.rotation);
                int back  = (isSolid(px + b1[0], py + b1[1]) ? 1 : 0) + (isSolid(px + b2[0], py + b2[1]) ? 1 : 0);
                int front = (isSolid(px + f1[0], py + f1[1]) ? 1 : 0) + (isSolid(px + f2[0], py + f2[1]) ? 1 : 0);
                if (front == 2 && back >= 1) {
                    spinType = SpinType.T_SPIN;
                } else if (back == 2 && front == 1) {
                    spinType = SpinType.T_SPIN_MINI;
                } else if (!canMovePiece(id, -1, 0) && !canMovePiece(id, 1, 0)
                        && !canMovePiece(id, 0, 1) && !canMovePiece(id, 0, -1)) {
                    spinType = SpinType.T_SPIN_MINI;
                }
            } else if (!canMovePiece(id, -1, 0) && !canMovePiece(id, 1, 0)
                    && !canMovePiece(id, 0, 1) && !canMovePiece(id, 0, -1)) {
                spinType = (p.type == Piece.I3 || p.type == Piece.L3)
                        ? SpinType.SMALL_SPIN : SpinType.ALL_SPIN;
            }
        }
        result.spinType = spinType;

        for (int i = 0; i < p.tiles.length; i++) {
            int mx = (int) Math.floor(p.location.x + p.tiles[i].x);
            int my = (int) Math.floor(p.location.y + p.tiles[i].y);
            if (mx < 0 || mx >= width || my < 0 || my >= height) continue;
            if (!allowedTiles[my][mx]) {
                result.brokenCells.add(new int[]{mx, my, p.type});
            } else {
                byte conn = (p.tileconnectionstates != null && i < p.tileconnectionstates.length)
                    ? p.tileconnectionstates[i] : Tile.SINGLE_TILE;
                board[my][mx].set(p.type, conn);
                result.placedCells.add(new int[]{mx, my});
            }
        }

        // Spawn replacement before clearing so the queue advances immediately
        spawnNextPiece(id);

        // Clear and settle
        clearAndSettle(result);

        return result;
    }

    /**
     * Detects full rows, records cleared cells, clears them, then drops entire rows
     * down to fill the cleared gaps.  Every row above a cleared row shifts down as a
     * unit (row-based compaction), so relative horizontal order is always preserved.
     * {@code allowedTiles=false} cells are permanent board features: their positions
     * are never overwritten.
     */
    private void clearAndSettle(LineClearResult r) {
        // Build set of x-columns the placed piece occupied, per row
        java.util.HashMap<Integer, java.util.ArrayList<Integer>> placedByRow =
            new java.util.HashMap<>();
        for (int[] cell : r.placedCells) {
            placedByRow.computeIfAbsent(cell[1], k -> new java.util.ArrayList<>()).add(cell[0]);
        }

        // Detect full rows (full = every column is either non-empty board tile or !allowedTiles)
        java.util.ArrayList<Integer> fullRows = new java.util.ArrayList<>();
        for (int y = 0; y < height; y++) {
            boolean full = true;
            for (int x = 0; x < width; x++) {
                if (allowedTiles[y][x] && board[y][x].get() == Tile.EMPTY) {
                    full = false;
                    break;
                }
            }
            if (full) fullRows.add(y);
        }

        if (fullRows.isEmpty()) return;

        // Fill result: clearedRows, filledColumnsPerClearedRow, clearedCells
        r.clearedRows = new int[fullRows.size()];
        java.util.HashSet<Integer> clearedRowSet = new java.util.HashSet<>();
        for (int i = 0; i < fullRows.size(); i++) {
            int y = fullRows.get(i);
            r.clearedRows[i] = y;
            clearedRowSet.add(y);

            java.util.ArrayList<Integer> placedCols = placedByRow.getOrDefault(y, new java.util.ArrayList<>());
            r.filledColumnsPerClearedRow.add(placedCols.stream().mapToInt(Integer::intValue).toArray());

            for (int x = 0; x < width; x++) {
                if (allowedTiles[y][x] && board[y][x].get() != Tile.EMPTY) {
                    r.clearedCells.add(new int[]{x, y, board[y][x].get()});
                }
            }
        }

        // Row-based compaction: walk rows bottom-to-top with a write pointer.
        // Cleared rows are skipped; all other rows are shifted down to fill the gaps.
        // allowedTiles=false cells are permanent and never written.
        int writeY = 0;
        for (int readY = 0; readY < height; readY++) {
            if (clearedRowSet.contains(readY)) continue; // skip cleared rows
            if (readY != writeY) {
                for (int x = 0; x < width; x++) {
                    if (allowedTiles[writeY][x]) {
                        // Source row may cross a barrier column — treat those as empty
                        byte src = allowedTiles[readY][x] ? board[readY][x].get() : Tile.EMPTY;
                        byte tex = allowedTiles[readY][x] ? board[readY][x].tex() : Tile.SINGLE_TILE;
                        board[writeY][x].set(src, tex);
                    }
                }
            }
            writeY++;
        }
        // Rows from writeY to height-1 are now vacant
        for (int y = writeY; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (allowedTiles[y][x]) {
                    board[y][x].set(Tile.EMPTY, Tile.SINGLE_TILE);
                }
            }
        }
    }

    /**
     * Spawns the next piece from {@code pieceQueues[id]} into {@code activePieces[id]}.
     * If the queue or spawn position for {@code id} doesn't exist the call is silently ignored.
     */
    public void spawnNextPiece(int id) {
        if (id < 0 || id >= pieceQueues.length || id >= spawnPositions.length) return;
        Piece next = Piece.defaultPiece(pieceQueues[id].takeNext());
        next.location.add(spawnPositions[id]);
        next.isBlockedFromSpawning = isSpawnBlocked(next);
        activePieces.set(id, next);
    }

    /**
     * Spawns a piece of {@code type} (from the shared hold slot swap) at {@code spawnPositions[id]}.
     * Sets the blocked flag appropriately. Used by server-side hold-while-blocked logic.
     */
    public void spawnHeldPiece(int id, byte type) {
        if (id < 0 || id >= spawnPositions.length || id >= activePieces.size()) return;
        Piece next = Piece.defaultPiece(type);
        next.location.add(spawnPositions[id]);
        next.isBlockedFromSpawning = isSpawnBlocked(next);
        activePieces.set(id, next);
    }

    /**
     * Returns true if the given piece overlaps at least one solid tile (out-of-bounds,
     * disallowed cell, or non-empty board tile) at its current location.
     * Other active pieces are NOT counted as blockers.
     */
    public boolean isSpawnBlocked(Piece p) {
        if (p == null || p.tiles == null || p.location == null) return false;
        for (Vector2 offset : p.tiles) {
            int x = (int) Math.floor(p.location.x + offset.x);
            int y = (int) Math.floor(p.location.y + offset.y);
            if (x < 0 || x >= width || y < 0 || y >= height) return true;
            if (!allowedTiles[y][x]) return true;
            if (board[y][x] != null && board[y][x].get() != Tile.EMPTY) return true;
        }
        return false;
    }

    /**
     * Returns where piece {@code id} would come to rest after a hard drop and whether
     * it would be locked, without modifying any game state.
     * Returns {@code null} if {@code id} is out of range.
     */
    public ShadowInfo getShadow(int id) {
        if (id < 0 || id >= activePieces.size()) return null;
        Piece p = activePieces.get(id);
        if (p.tiles == null || p.location == null) return null;

        float sx = p.location.x;
        float sy = p.location.y;
        while (canPieceBeAt(id, sx, sy - 1)) sy--;

        ShadowInfo info = new ShadowInfo();
        info.locationX = sx;
        info.locationY = sy;

        // Solid support check (same logic as hardDrop)
        for (Vector2 offset : p.tiles) {
            int mx = (int) Math.floor(sx + offset.x);
            int my = (int) Math.floor(sy + offset.y);
            int below = my - 1;
            if (below < 0) { info.wouldPlace = true; break; }
            if (mx >= 0 && mx < width && below >= 0 && below < height) {
                if (!allowedTiles[below][mx]) { info.wouldPlace = true; break; }
                if (board[below][mx].get() != Tile.EMPTY) { info.wouldPlace = true; break; }
            }
        }
        return info;
    }

    /**
     * Checks whether piece {@code id} can occupy the position with anchor ({@code baseX}, {@code baseY})
     * without mutating any state.  Mirrors the logic in {@link #canMovePiece} exactly.
     */
    private boolean canPieceBeAt(int id, float baseX, float baseY) {
        Piece p = activePieces.get(id);
        for (int i = 0; i < p.tiles.length; i++) {
            float lx = baseX + p.tiles[i].x;
            float ly = baseY + p.tiles[i].y;
            if (lx < 0 || ly < 0 || lx >= width || ly >= height) return false;
            int ix = (int) lx, iy = (int) ly;
            if (board[iy][ix] == null || board[iy][ix].get() != 0) return false;
            if (!allowedTiles[iy][ix]) return false;
            for (int j = 0; j < activePieces.size(); j++) {
                if (j == id) continue;
                for (Vector2 t : activePieces.get(j).tiles) {
                    if (lx == t.x + activePieces.get(j).location.x &&
                        ly == t.y + activePieces.get(j).location.y) return false;
                }
            }
        }
        return true;
    }

    /** Result of {@link Board#getShadow(int)}. */
    public static class ShadowInfo {
        /** Shadow anchor X in board-tile space. */
        public float locationX;
        /** Shadow anchor Y in board-tile space. */
        public float locationY;
        /** True when the piece would actually be locked here (has solid support below). */
        public boolean wouldPlace;
    }

    // Net

    public NetBoardLight convertToNetBoardLight() {
        NetBoardLight retval = new NetBoardLight();
        retval.tileid = new byte[width*height];
        retval.tileconnections = new byte[width*height];
        retval.pieces = new Piece.NetPiece[activePieces.size()];
        for (int y=0; y<height; y++) {
            for (int x=0; x<width; x++) {
                retval.tileid[y*width + x] = board[y][x].get();
                retval.tileconnections[y*width + x] = board[y][x].tex();
            }
        }
        for (int i=0; i<activePieces.size(); i++) {
            retval.pieces[i] = activePieces.get(i).convertToNetPiece();
        }
        retval.heldPieceType = heldPieceType;
        retval.playerHoldUsed = (playerHoldUsed != null)
            ? Arrays.copyOf(playerHoldUsed, playerHoldUsed.length)
            : new boolean[spawnPositions.length];
        return retval;
    }

    public NetBoardFull convertToNetBoardFull() {
        NetBoardFull retval = new NetBoardFull();
        retval.tileid = new byte[width*height];
        retval.tileconnections = new byte[width*height];
        retval.allowedtiles = new boolean[width*height];
        retval.width = width;
        retval.height = height;
        retval.spawnposx = new byte[spawnPositions.length];
        retval.spawnposy = new byte[spawnPositions.length];
        retval.queues = new PieceQueue.NetQueue[pieceQueues.length];
        retval.pieces = new Piece.NetPiece[activePieces.size()];
        for (int y=0; y<height; y++) {
            for (int x=0; x<width; x++) {
                retval.tileid[y*width + x] = board[y][x].get();
                retval.tileconnections[y*width + x] = board[y][x].tex();
                retval.allowedtiles[y*width + x] = allowedTiles[y][x];
            }
        }
        for (int i=0; i<spawnPositions.length; i++) {
            retval.spawnposx[i] = (byte) Math.floor(spawnPositions[i].x);
            retval.spawnposy[i] = (byte) Math.floor(spawnPositions[i].y);
        }
        for (int i=0; i<pieceQueues.length; i++) {
            retval.queues[i] = pieceQueues[i].convertToNetQueue();
        }
        for (int i=0; i<activePieces.size(); i++) {
            retval.pieces[i] = activePieces.get(i).convertToNetPiece();
        }
        return retval;
    }

    public void updateFromNetBoardLight(NetBoardLight in) {
        for (int y=0; y<height; y++) {
            for (int x=0; x<width; x++) {
                board[y][x].set(in.tileid[y*width + x], in.tileconnections[y*width + x]);
            }
        }
        for (int i=0; i<in.pieces.length; i++) {
            if (activePieces.size() == i) {
                activePieces.add(Piece.createFromNetPiece(in.pieces[i]));
                continue;
            }
            if (activePieces.get(i).type == in.pieces[i].type) {
                activePieces.get(i).updateFromNetPiece(in.pieces[i]); // avoids creating a new object on every update unless it is a new piece
            } else {
                activePieces.set(i, Piece.createFromNetPiece(in.pieces[i]));
            }
        }
        heldPieceType = in.heldPieceType;
        if (in.playerHoldUsed != null) {
            playerHoldUsed = Arrays.copyOf(in.playerHoldUsed, in.playerHoldUsed.length);
        }
    }

    // Static

    public static enum Presets {
        STANDARD_SINGLE, // normal board in most tetris games, 10 wide
        STANDARD_DUO,
        STANDARD_TRIO,
        STANDARD_4P,
        TEST
    }

    public static class NetBoardLight { // smaller class to be sent over UDP constantly (20-30 times/sec)
        public byte[] tileid;
        public byte[] tileconnections;
        public Piece.NetPiece[] pieces;
        public byte heldPieceType;
        public boolean[] playerHoldUsed;
    }

    public static NetBoardLight lightNetBoardFrom(NetBoardFull full) {
        NetBoardLight retval = new NetBoardLight();
        retval.pieces = full.pieces;
        retval.tileid = full.tileid;
        retval.tileconnections = full.tileconnections;
        return retval;
    }

    public static class NetBoardFull { // bigger class, sent initially and then rarely on desyncs (avoiding excessive bandwidth usage)
        public byte[] tileid;
        public byte[] tileconnections;
        public boolean[] allowedtiles;
        public byte width;
        public byte height;
        public byte[] spawnposx;
        public byte[] spawnposy;
        public PieceQueue.NetQueue[] queues;
        public Piece.NetPiece[] pieces;
    }
}
