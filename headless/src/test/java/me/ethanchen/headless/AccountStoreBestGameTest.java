package me.ethanchen.headless;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.progression.BestGameRecord;
import me.ethanchen.game.progression.PlayerProfile;
import me.ethanchen.server.GameResultData;
import me.ethanchen.server.PublicAccountView;
import me.ethanchen.server.ResultRecorder;

class AccountStoreBestGameTest {

    @Test
    void considerBestGame_keepsHigherScoreAndSurvivesProfileSave() throws Exception {
        Path dir = Files.createTempDirectory("us-accounts");
        AccountStore store = new AccountStore(dir.resolve("accounts.db").toString());
        try {
            assertNull(store.createAccount("Alice", "secret"));
            Account acct = store.authenticate("Alice", "secret");
            assertNotNull(acct);

            store.considerBestGame(acct.uuid, new BestGameRecord("g1", "MULTIPLAYER_SCORE", 100, "100"));
            store.considerBestGame(acct.uuid, new BestGameRecord("g2", "MULTIPLAYER_SCORE", 50, "50"));
            store.considerBestGame(acct.uuid, new BestGameRecord("g3", "MULTIPLAYER_SCORE", 200, "200"));
            store.considerBestGame(acct.uuid, new BestGameRecord("p1", "MULTIPLAYER_PUZZLE", 9, "0:10"));

            PlayerProfile profile = store.loadProfile(acct.uuid);
            profile.selectedCharacterId = 1;
            store.saveProfile(acct.uuid, profile);

            PublicAccountView view = store.loadPublicView(acct.uuid);
            assertNotNull(view);
            assertEquals(acct.uuid, view.accountUuid);
            assertEquals("alice", view.username);
            assertEquals(1, view.selectedCharacterId);
            assertEquals(200L, view.bestScore.score);
            assertEquals("200", view.bestScore.displayScore);
            assertEquals("0:10", view.bestPuzzle.displayScore);
            assertNull(view.bestCharacterScore);
        } finally {
            store.close();
        }
    }

    @Test
    void ensureBestsBackfilled_fillsLegacyAccountOnce() throws Exception {
        Path dir = Files.createTempDirectory("us-accounts-bf");
        AccountStore store = new AccountStore(dir.resolve("accounts.db").toString());
        try {
            assertNull(store.createAccount("Bob", "secret"));
            Account acct = store.authenticate("Bob", "secret");
            assertNotNull(acct);
            markUnmigrated(store, acct.uuid);

            ResultRecorder results = new ResultRecorder() {
                @Override
                public String recordGameResult(GameResultData data) {
                    return null;
                }

                @Override
                public BestGameRecord bestGame(String accountUuid, String gamemode) {
                    if ("MULTIPLAYER_PUZZLE".equals(gamemode)) {
                        return new BestGameRecord("p", gamemode, 42, "0:08");
                    }
                    return null;
                }
            };
            store.ensureBestsBackfilled(acct.uuid, results);
            store.ensureBestsBackfilled(acct.uuid, new ResultRecorder() {
                @Override
                public String recordGameResult(GameResultData data) {
                    return null;
                }

                @Override
                public BestGameRecord bestGame(String accountUuid, String gamemode) {
                    return new BestGameRecord("later", gamemode, 1, "should-not-apply");
                }
            });

            PublicAccountView view = store.loadPublicView(acct.uuid);
            assertEquals("0:08", view.bestPuzzle.displayScore);
            assertNull(view.bestScore);
        } finally {
            store.close();
        }
    }

    @SuppressWarnings("unchecked")
    private static void markUnmigrated(AccountStore store, String uuid) throws Exception {
        java.lang.reflect.Field field = AccountStore.class.getDeclaredField("extraCache");
        field.setAccessible(true);
        java.util.Map<String, AccountExtra> cache = (java.util.Map<String, AccountExtra>) field.get(store);
        cache.get(uuid).bestsMigrated = false;
    }
}
