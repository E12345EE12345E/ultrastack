package me.ethanchen.game.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

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
    void isSpawnBlocked_trueWhenOverlappingExistingTile() {
        Board board = new Board(Board.Presets.STANDARD_SINGLE);
        board.getBoard()[20][4].set(Tile.GARBAGE, Tile.SINGLE_TILE);

        Piece piece = Piece.defaultPiece(Piece.O);
        piece.location.set(4.5f, 20.5f);

        assertTrue(board.isSpawnBlocked(piece));
    }
}
