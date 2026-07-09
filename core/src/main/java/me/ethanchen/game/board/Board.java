package me.ethanchen.game.board;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import com.badlogic.gdx.math.Vector2;

import me.ethanchen.game.GameConstants;
import me.ethanchen.network.dto.NetBoardFull;
import me.ethanchen.network.dto.NetBoardLight;
import me.ethanchen.network.dto.NetPiece;
import me.ethanchen.network.dto.NetQueue;

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
        BoardPreset p = BoardPreset.of(preset);
        this.width = p.width;
        this.height = p.height;
        this.allowedTiles = p.allowedTiles;
        this.board = emptyBoard();
        this.spawnPositions = p.spawnPositions;
        this.pieceQueues = p.pieceQueues;
        this.activePieces = new ArrayList<>();
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

    /** Returns true if any tile on the board is currently a garbage tile. */
    public boolean hasGarbage() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (board[y][x].get() == Tile.GARBAGE) return true;
            }
        }
        return false;
    }

    /**
     * Fills the bottom {@code numLines} rows with garbage tiles, each row leaving exactly
     * one random gap column so it can be cleared by filling that column in.
     */
    public void spawnGarbageLines(int numLines) {
        Random r = new Random();
        for (int y = 0; y < numLines && y < height; y++) {
            int gapCol = r.nextInt(width);
            for (int x = 0; x < width; x++) {
                if (!allowedTiles[y][x]) continue;
                board[y][x].set(x == gapCol ? Tile.EMPTY : Tile.GARBAGE, Tile.SINGLE_TILE);
            }
        }
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
        return BoardCollision.canMovePiece(this, id, xdiff, ydiff);
    }

    public boolean moveLeft(int id) {
        if (canMovePiece(id, -1, 0)) {
            Piece p = activePieces.get(id);
            if (p.lockTime > 0) { p.lockedMovementCounter++; p.lockTime = 0f; }
            p.location.add(-1, 0);
            p.lastMoveWasRotation = false;
            return true;
        }
        return false;
    }

    public boolean moveRight(int id) {
        if (canMovePiece(id, 1, 0)) {
            Piece p = activePieces.get(id);
            if (p.lockTime > 0) { p.lockedMovementCounter++; p.lockTime = 0f; }
            p.location.add(1, 0);
            p.lastMoveWasRotation = false;
            return true;
        }
        return false;
    }

    public boolean moveDown(int id) {
        if (canMovePiece(id, 0, -1)) {
            Piece p = activePieces.get(id);
            if (p.lockTime > 0) { p.lockedMovementCounter++; p.lockTime = 0f; }
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
            if (p.lockTime > 0) { p.lockedMovementCounter++; p.lockTime = 0f; }
            p.rotateTexCW();
            p.lastMoveWasRotation = true;
            return true;
        }
        Vector2[] kicks = BoardCollision.kickTableFor(p.type);
        if (kicks != null && BoardCollision.tryKicks(this, id, fromRotation * 2, kicks)) {
            if (p.lockTime > 0) { p.lockedMovementCounter++; p.lockTime = 0f; }
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
            if (p.lockTime > 0) { p.lockedMovementCounter++; p.lockTime = 0f; }
            p.rotateTexCCW();
            p.lastMoveWasRotation = true;
            return true;
        }
        Vector2[] kicks = BoardCollision.kickTableFor(p.type);
        int row = (fromRotation == 0) ? 7 : fromRotation * 2 - 1;
        if (kicks != null && BoardCollision.tryKicks(this, id, row, kicks)) {
            if (p.lockTime > 0) { p.lockedMovementCounter++; p.lockTime = 0f; }
            p.rotateTexCCW();
            p.lastMoveWasRotation = true;
            return true;
        }
        p.rotateCW();
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
            if (p.lockTime > 0) { p.lockedMovementCounter++; p.lockTime = 0f; }
            p.rotateTexCW(); p.rotateTexCW();
            p.lastMoveWasRotation = true;
            return true;
        }
        Vector2[] kicks180 = BoardCollision.kickTable180For(p.type);
        if (kicks180 != null) {
            int stride = (p.type == Piece.I) ? 1 : 5;
            if (BoardCollision.tryKicks180(this, id, fromRotation, kicks180, stride)) {
                if (p.lockTime > 0) { p.lockedMovementCounter++; p.lockTime = 0f; }
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
            case SOFT_DROP:
                return moveDown(pieceId);
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

        return lockPieceInPlace(id, dropDistance, true);
    }

    /**
     * Locks piece {@code id} in-place (no drop) — identical to {@link #hardDrop(int)}
     * but skips the slide-down step.  Sets {@code result.manual = false}.
     * Returns {@code null} if the id is out of range or the piece is spawn-blocked.
     */
    public LineClearResult lockDrop(int id) {
        if (id < 0 || id >= activePieces.size()) return null;
        if (activePieces.get(id).isBlockedFromSpawning) return null;

        return lockPieceInPlace(id, 0, false);
    }

    /**
     * Shared placement logic used by {@link #hardDrop(int)} and {@link #lockDrop(int)}: builds
     * the {@link LineClearResult}, checks for solid support, locks the piece's minoes onto the
     * board if supported, detects spins, spawns the next piece, and clears/settles full rows.
     *
     * @param dropDistance number of cells the piece slid down before locking (always 0 for
     *                      {@link #lockDrop(int)}); used only for spin-detection eligibility
     * @param manual        true for a player-issued hard drop, false for an automatic lock
     */
    private LineClearResult lockPieceInPlace(int id, int dropDistance, boolean manual) {
        Piece p = activePieces.get(id);
        LineClearResult result = new LineClearResult();
        result.playerId = id;
        result.pieceType = p.type;
        result.restingX = (int) Math.floor(p.location.x);
        result.restingY = (int) Math.floor(p.location.y);
        result.restingCenterX = p.location.x;
        result.restingCenterY = p.location.y;
        result.pieceRotation = p.rotation;
        result.manual = manual;

        // Check for solid support (floor, board tile, or disallowed cell below each mino)
        boolean hasSolidSupport = BoardCollision.hasSolidSupportAt(this, p, p.location.x, p.location.y);

        if (!hasSolidSupport) {
            result.placed = false;
            result.blockedByPlayerId = BoardCollision.findRestingBlocker(this, id);
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
                int back  = (BoardCollision.isSolid(this, px + b1[0], py + b1[1]) ? 1 : 0) + (BoardCollision.isSolid(this, px + b2[0], py + b2[1]) ? 1 : 0);
                int front = (BoardCollision.isSolid(this, px + f1[0], py + f1[1]) ? 1 : 0) + (BoardCollision.isSolid(this, px + f2[0], py + f2[1]) ? 1 : 0);
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
        BoardLineClear.clearAndSettle(this, result);

        return result;
    }

    /**
     * If piece {@code id} has moved more than {@link GameConstants#MOVEMENT_LOCK_COUNTER_LIMIT}
     * times while grounded AND currently has solid support, locks it immediately via
     * {@link #lockDrop(int)}. Returns the {@link LineClearResult} if a lock occurred, or
     * {@code null} otherwise.
     */
    public LineClearResult tryMovementLock(int id) {
        if (id < 0 || id >= activePieces.size()) return null;
        Piece p = activePieces.get(id);
        if (p.lockedMovementCounter > GameConstants.MOVEMENT_LOCK_COUNTER_LIMIT
                && BoardCollision.hasSolidSupportNow(this, id))
            return lockDrop(id);
        return null;
    }

    /**
     * Advances lock timers for all active pieces by {@code deltaMs} milliseconds.
     * Pieces with solid support accumulate time; pieces without solid support are reset.
     * Any piece whose timer reaches {@link GameConstants#LOCK_DELAY_MS} is locked in-place
     * via {@link #lockDrop(int)}.
     *
     * @return list of {@link LineClearResult} for any pieces that were auto-locked this tick
     */
    public ArrayList<LineClearResult> updateLockTimers(int deltaMs) {
        ArrayList<LineClearResult> results = new ArrayList<>();
        for (int i = 0; i < activePieces.size(); i++) {
            Piece p = activePieces.get(i);
            if (p.isBlockedFromSpawning) continue;
            if (BoardCollision.hasSolidSupportNow(this, i)) {
                p.lockTime += deltaMs;
                if (p.lockTime >= GameConstants.LOCK_DELAY_MS) {
                    LineClearResult r = lockDrop(i);
                    if (r != null) results.add(r);
                }
            } else {
                p.lockTime = 0f;
            }
        }
        return results;
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
        while (BoardCollision.canPieceBeAt(this, id, sx, sy - 1)) sy--;

        ShadowInfo info = new ShadowInfo();
        info.locationX = sx;
        info.locationY = sy;
        info.wouldPlace = BoardCollision.hasSolidSupportAt(this, p, sx, sy);
        return info;
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
        retval.pieces = new NetPiece[activePieces.size()];
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
        retval.queues = new NetQueue[pieceQueues.length];
        retval.pieces = new NetPiece[activePieces.size()];
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
        // Remove any stale entries left over from a previous, longer snapshot.
        while (activePieces.size() > in.pieces.length) {
            activePieces.remove(activePieces.size() - 1);
        }
        heldPieceType = in.heldPieceType;
        if (in.playerHoldUsed != null) {
            playerHoldUsed = Arrays.copyOf(in.playerHoldUsed, in.playerHoldUsed.length);
        }
    }

    // Static

    public enum Presets {
        STANDARD_SINGLE, // normal board in most tetris games, 10 wide
        STANDARD_DUO,
        STANDARD_TRIO,
        STANDARD_4P,
        SHORT_SINGLE,
        SHORT_DUO,
        SHORT_TRIO,
        SHORT_4P
    }

    public static NetBoardLight lightNetBoardFrom(NetBoardFull full) {
        NetBoardLight retval = new NetBoardLight();
        retval.pieces = full.pieces;
        retval.tileid = full.tileid;
        retval.tileconnections = full.tileconnections;
        return retval;
    }
}
