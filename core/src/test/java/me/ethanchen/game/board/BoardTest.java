package me.ethanchen.game.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.math.Vector2;

/**
 * Characterization tests for {@link Board} collision and line-clear/compaction behavior.
 * These lock in current behavior before the god-class split described in the refactoring plan.
 */
class BoardTest {

    @Test
    void canMovePiece_rejectsOutOfBoundsAndAllowsValidMoves() {
        Board board = new Board(Board.Presets.STANDARD_SINGLE);
        board.spawnInitialPieces();

        // Repeatedly moving left must eventually be rejected once the piece hits the wall.
        int guard = 0;
        while (board.moveLeft(0) && guard < 100) guard++;
        assertFalse(board.canMovePiece(0, -1, 0), "piece should be flush against the left wall");

        // From the wall, moving right must be legal.
        assertTrue(board.canMovePiece(0, 1, 0));

        // Way out of bounds must always be rejected.
        assertFalse(board.canMovePiece(0, -1000, 0));
        assertFalse(board.canMovePiece(0, 0, -1000));
    }

    @Test
    void hardDrop_locksPieceWithoutFurtherDropWhenAlreadySupported() {
        Board board = new Board(Board.Presets.STANDARD_SINGLE);
        board.spawnInitialPieces();

        // Fill bottom row except columns 4-5, leaving a slot for an O piece to complete it.
        Tile[][] cells = board.getBoard();
        for (int x = 0; x < board.bw(); x++) {
            if (x == 4 || x == 5) continue;
            cells[0][x].set(Tile.GARBAGE, Tile.SINGLE_TILE);
        }

        // Place an O piece straddling rows 0-1, columns 4-5 (already resting on the floor).
        Piece oPiece = Piece.defaultPiece(Piece.O);
        oPiece.location.set(4.5f, 0.5f);
        board.getActivePieces().set(0, oPiece);

        LineClearResult result = board.hardDrop(0);

        assertNotNull(result);
        assertTrue(result.placed);
        assertEquals(1, result.numClearedRows());
        assertEquals(1, result.clearedRows.length);
        assertEquals(0, result.clearedRows[0]);

        // Row 0 cleared, so row 1's O cells (cols 4,5) should have compacted down into row 0;
        // everything else in row 0 should now be empty.
        assertEquals(Piece.O, cells[0][4].get());
        assertEquals(Piece.O, cells[0][5].get());
        for (int x = 0; x < board.bw(); x++) {
            if (x == 4 || x == 5) continue;
            assertEquals(Tile.EMPTY, cells[0][x].get(), "column " + x + " should be empty after compaction");
        }
    }

    @Test
    void hardDrop_doesNotPlaceWhenRestingSolelyOnAnotherPlayersPiece() {
        Board board = new Board(Board.Presets.STANDARD_DUO);
        board.spawnInitialPieces();

        // Player 1's piece occupies the floor at columns 0-1, rows 0.
        Piece support = Piece.defaultPiece(Piece.O);
        support.location.set(0.5f, 0.5f);
        board.getActivePieces().set(1, support);

        // Player 0's piece rests directly on top of player 1's piece (no board support).
        Piece dropper = Piece.defaultPiece(Piece.O);
        dropper.location.set(0.5f, 1.5f);
        board.getActivePieces().set(0, dropper);

        LineClearResult result = board.hardDrop(0);

        assertNotNull(result);
        assertFalse(result.placed, "piece resting purely on another player's piece must not lock");
        assertEquals(1, result.blockedByPlayerId);
    }

    @Test
    void lineClear_pushesPieceDownUnderDescendingOverhang() {
        Board board = new Board(Board.Presets.STANDARD_DUO);
        board.spawnInitialPieces();

        Tile[][] cells = board.getBoard();
        // Fill rows 0 and 1 completely so both will be cleared.
        for (int x = 0; x < board.bw(); x++) {
            cells[0][x].set(Tile.GARBAGE, Tile.SINGLE_TILE);
            cells[1][x].set(Tile.GARBAGE, Tile.SINGLE_TILE);
        }
        // A one-tall overhang, one empty row above where player 1's piece sits.
        cells[5][4].set(Tile.GARBAGE, Tile.SINGLE_TILE);
        cells[5][5].set(Tile.GARBAGE, Tile.SINGLE_TILE);

        // Player 1's piece sits in the gap under the overhang, above the rows to be cleared.
        Piece underOverhang = Piece.defaultPiece(Piece.O);
        underOverhang.location.set(4.5f, 3.5f);
        board.getActivePieces().set(1, underOverhang);

        // Player 0's piece lands on top of the already-full rows 0-1 in unrelated columns,
        // just to trigger the clear without disturbing the setup above.
        Piece dropper = Piece.defaultPiece(Piece.O);
        dropper.location.set(0.5f, 20.5f);
        board.getActivePieces().set(0, dropper);

        LineClearResult result = board.hardDrop(0);

        assertNotNull(result);
        assertTrue(result.placed);
        assertEquals(2, result.numClearedRows());

        // Two rows clear below the overhang, so it drops from row 5 to row 3, and the piece
        // (originally resting one empty row below it) must be pushed down by 2 to stay clear.
        Piece pushed = board.getActivePiece(1);
        assertEquals(1.5f, pushed.location.y, 0.001f);

        for (Vector2 offset : pushed.tiles) {
            int x = (int) Math.floor(pushed.location.x + offset.x);
            int y = (int) Math.floor(pushed.location.y + offset.y);
            assertEquals(Tile.EMPTY, cells[y][x].get(),
                "pushed piece must not intersect a locked tile at (" + x + "," + y + ")");
        }
    }

    @Test
    void lineClear_doesNotMovePieceInOpenAirWithNoOverhangAbove() {
        Board board = new Board(Board.Presets.STANDARD_DUO);
        board.spawnInitialPieces();

        Tile[][] cells = board.getBoard();
        for (int x = 0; x < board.bw(); x++) {
            cells[0][x].set(Tile.GARBAGE, Tile.SINGLE_TILE);
            cells[1][x].set(Tile.GARBAGE, Tile.SINGLE_TILE);
        }
        // No overhang above the piece this time.
        Piece inOpenAir = Piece.defaultPiece(Piece.O);
        inOpenAir.location.set(4.5f, 3.5f);
        board.getActivePieces().set(1, inOpenAir);

        Piece dropper = Piece.defaultPiece(Piece.O);
        dropper.location.set(0.5f, 20.5f);
        board.getActivePieces().set(0, dropper);

        LineClearResult result = board.hardDrop(0);

        assertNotNull(result);
        assertTrue(result.placed);
        assertEquals(2, result.numClearedRows());
        assertEquals(3.5f, board.getActivePiece(1).location.y, 0.001f,
            "a piece with nothing above it should not be moved by a line clear");
    }

    @Test
    void lineClear_doesNotMovePieceWhenOverhangGapExceedsClearedRows() {
        Board board = new Board(Board.Presets.STANDARD_DUO);
        board.spawnInitialPieces();

        Tile[][] cells = board.getBoard();
        // Only row 0 will be full; row 1 is deliberately left with one empty column so it
        // does not clear, meaning only a single row is removed below the overhang.
        for (int x = 0; x < board.bw(); x++) {
            cells[0][x].set(Tile.GARBAGE, Tile.SINGLE_TILE);
        }
        for (int x = 0; x < board.bw() - 1; x++) {
            cells[1][x].set(Tile.GARBAGE, Tile.SINGLE_TILE);
        }

        // Two-row gap between the piece (top row 4) and the overhang (row 6).
        cells[6][4].set(Tile.GARBAGE, Tile.SINGLE_TILE);
        cells[6][5].set(Tile.GARBAGE, Tile.SINGLE_TILE);

        Piece underFarOverhang = Piece.defaultPiece(Piece.O);
        underFarOverhang.location.set(4.5f, 3.5f);
        board.getActivePieces().set(1, underFarOverhang);

        Piece dropper = Piece.defaultPiece(Piece.O);
        dropper.location.set(0.5f, 20.5f);
        board.getActivePieces().set(0, dropper);

        LineClearResult result = board.hardDrop(0);

        assertNotNull(result);
        assertTrue(result.placed);
        assertEquals(1, result.numClearedRows());
        assertEquals(3.5f, board.getActivePiece(1).location.y, 0.001f,
            "the overhang only drops by 1 and the 2-row gap is still enough clearance");
    }

    @Test
    void lineClear_chainPushesStackedPiecesUnderOverhang() {
        Board board = new Board(Board.Presets.STANDARD_TRIO);
        board.spawnInitialPieces();

        Tile[][] cells = board.getBoard();
        for (int x = 0; x < board.bw(); x++) {
            cells[0][x].set(Tile.GARBAGE, Tile.SINGLE_TILE);
            cells[1][x].set(Tile.GARBAGE, Tile.SINGLE_TILE);
        }
        // One-tall overhang, one empty row above the top of the stacked pair below it.
        cells[7][4].set(Tile.GARBAGE, Tile.SINGLE_TILE);
        cells[7][5].set(Tile.GARBAGE, Tile.SINGLE_TILE);

        // Two pieces stacked directly on top of each other, both under the overhang.
        Piece lower = Piece.defaultPiece(Piece.O);
        lower.location.set(4.5f, 3.5f);
        board.getActivePieces().set(1, lower);

        Piece upper = Piece.defaultPiece(Piece.O);
        upper.location.set(4.5f, 5.5f);
        board.getActivePieces().set(2, upper);

        Piece dropper = Piece.defaultPiece(Piece.O);
        dropper.location.set(0.5f, 20.5f);
        board.getActivePieces().set(0, dropper);

        LineClearResult result = board.hardDrop(0);

        assertNotNull(result);
        assertTrue(result.placed);
        assertEquals(2, result.numClearedRows());

        // Both pieces must be pushed down by 2, preserving their relative stacking, and
        // neither may end up intersecting the other or the settled overhang.
        assertEquals(1.5f, board.getActivePiece(1).location.y, 0.001f);
        assertEquals(3.5f, board.getActivePiece(2).location.y, 0.001f);

        for (int pid : new int[]{1, 2}) {
            Piece p = board.getActivePiece(pid);
            for (Vector2 offset : p.tiles) {
                int x = (int) Math.floor(p.location.x + offset.x);
                int y = (int) Math.floor(p.location.y + offset.y);
                assertEquals(Tile.EMPTY, cells[y][x].get(),
                    "piece " + pid + " must not intersect a locked tile at (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void isSpawnBlocked_trueWhenOverlappingExistingTile() {
        Board board = new Board(Board.Presets.STANDARD_SINGLE);
        board.getBoard()[20][4].set(Tile.GARBAGE, Tile.SINGLE_TILE);

        Piece piece = Piece.defaultPiece(Piece.O);
        piece.location.set(4.5f, 20.5f);

        assertTrue(board.isSpawnBlocked(piece));
    }
}
