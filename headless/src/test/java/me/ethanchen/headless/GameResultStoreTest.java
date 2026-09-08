package me.ethanchen.headless;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.progression.BestGameRecord;
import me.ethanchen.server.GameResultData;
import me.ethanchen.server.PlayerResultInfo;

class GameResultStoreTest {

    @Test
    void bestGame_returnsDisplayScoreAndIgnoresDisconnected() throws Exception {
        Path dir = Files.createTempDirectory("us-results");
        Path db = dir.resolve("results.db");
        GameResultStore store = new GameResultStore(db.toString());
        try {
            String uuid = "player-1";
            insert(store, "MULTIPLAYER_SCORE", 500, "500", uuid, false, 1_000L);
            insert(store, "MULTIPLAYER_SCORE", 9_999, "9,999", uuid, true, 2_000L);
            insert(store, "MULTIPLAYER_SCORE", 1_200, "1,200", uuid, false, 3_000L);
            long puzzleRaw = Integer.MAX_VALUE - 90_000L;
            insert(store, "MULTIPLAYER_PUZZLE", puzzleRaw, "1:30", uuid, false, 4_000L);

            BestGameRecord score = store.bestGame(uuid, "MULTIPLAYER_SCORE");
            assertNotNull(score);
            assertEquals(1_200L, score.score);
            assertEquals("1,200", score.displayScore);
            assertEquals(3_000L, score.timestampMs);
            assertEquals(1, score.playerNames.length);
            assertEquals("alice", score.playerNames[0]);

            BestGameRecord puzzle = store.bestGame(uuid, "MULTIPLAYER_PUZZLE");
            assertNotNull(puzzle);
            assertEquals("1:30", puzzle.displayScore);
            assertEquals(puzzleRaw, puzzle.score);

            assertNull(store.bestGame(uuid, "CHARACTER_SCORE"));
            assertNull(store.bestGame("other", "MULTIPLAYER_SCORE"));
        } finally {
            store.close();
        }
    }

    private static void insert(GameResultStore store, String gamemode, long score, String display,
                               String accountUuid, boolean disconnected, long timestampMs) {
        GameResultData data = new GameResultData();
        data.gamemode = gamemode;
        data.score = score;
        data.displayScore = display;
        data.players = new PlayerResultInfo[]{ new PlayerResultInfo("alice", accountUuid) };
        data.win = true;
        data.disconnected = disconnected;
        data.timestampMs = timestampMs;
        assertNotNull(store.recordGameResult(data));
    }
}
