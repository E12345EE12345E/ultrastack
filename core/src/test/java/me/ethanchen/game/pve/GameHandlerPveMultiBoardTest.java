package me.ethanchen.game.pve;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.GameHandler;
import me.ethanchen.game.GameMode;

/**
 * Verifies the true multi-board support added for PvE (implementation.md, Part 6, Phase 1):
 * {@link GameHandler#init(GameMode, me.ethanchen.game.GameModeRules, long)} builds one real
 * {@link me.ethanchen.game.board.Board} per entry in {@code rules.boardLayout(numPlayers)} and
 * resolves each global slot to the right board/seat via {@code rules.slotToBoard(numPlayers)}.
 */
class GameHandlerPveMultiBoardTest {

    private static PveLevelData fixtureLevel() {
        PveLevelData level = new PveLevelData();
        PveSection section = new PveSection();
        section.pass = new PveCriterion[][]{{ new PveCriterion(PveCriterionType.SCORE, 100) }};
        level.sections = new PveSection[]{ section };

        level.board1 = new PveBoardSpec();
        level.board1.width = 10;
        level.board1.height = 20;
        level.board1.spawns = new int[][]{{4, 18}};

        level.board2 = new PveBoardSpec();
        level.board2.width = 10;
        level.board2.height = 20;
        level.board2.spawns = new int[][]{{2, 18}, {6, 18}};
        return level;
    }

    private GameHandler init(int numPlayers) {
        GameHandler game = new GameHandler(numPlayers);
        game.init(GameMode.PVE, new PveRules(fixtureLevel()), 0);
        return game;
    }

    @Test
    void onePlayerGetsOneBoardOfOneSeat() {
        GameHandler game = init(1);
        assertEquals(1, game.getBoards().size());
        assertEquals(0, game.boardIndexOf(0));
        assertEquals(0, game.seatOf(0));
    }

    @Test
    void twoPlayersShareOneBoard() {
        GameHandler game = init(2);
        assertEquals(1, game.getBoards().size());
        assertEquals(0, game.boardIndexOf(0));
        assertEquals(0, game.boardIndexOf(1));
        assertArrayEquals(new int[]{0, 1}, game.slotsOnBoard(0));
    }

    @Test
    void threePlayersSplitIntoBoardsOfOneAndTwo() {
        GameHandler game = init(3);
        assertEquals(2, game.getBoards().size());
        assertEquals(0, game.boardIndexOf(0));
        assertEquals(1, game.boardIndexOf(1));
        assertEquals(1, game.boardIndexOf(2));
        assertArrayEquals(new int[]{0}, game.slotsOnBoard(0));
        assertArrayEquals(new int[]{1, 2}, game.slotsOnBoard(1));
        // Board 0 has 1 seat (matches board1 spec), board 1 has 2 seats (matches board2 spec).
        assertEquals(1, game.getBoards().get(0).getSpawnPositions().length);
        assertEquals(2, game.getBoards().get(1).getSpawnPositions().length);
    }

    @Test
    void fourPlayersSplitIntoTwoBoardsOfTwo() {
        GameHandler game = init(4);
        assertEquals(2, game.getBoards().size());
        assertArrayEquals(new int[]{0, 1}, game.slotsOnBoard(0));
        assertArrayEquals(new int[]{2, 3}, game.slotsOnBoard(1));
        assertEquals(0, game.seatOf(2));
        assertEquals(1, game.seatOf(3));
    }

    @Test
    void existingSingleBoardModesAreUnaffected() {
        // Regression: MULTIPLAYER_SCORE and friends must still build exactly one board.
        GameHandler game = new GameHandler(4);
        game.init(GameMode.MULTIPLAYER_SCORE, 0);
        assertEquals(1, game.getBoards().size());
        for (int slot = 0; slot < 4; slot++) {
            assertEquals(0, game.boardIndexOf(slot));
        }
    }
}
