package me.ethanchen.headless;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.progression.PlayerProfile;
import me.ethanchen.server.Session;

class AccountStoreTokenBonusTest {
    @Test
    void newAccountStartsWithBonusAlreadyMarkedReceived() throws Exception {
        Path dir = Files.createTempDirectory("us-account-new-bonus");
        AccountStore store = new AccountStore(dir.resolve("accounts.db").toString());
        try {
            assertNull(store.createAccount("NewPlayer", "secret"));
            Account account = store.authenticate("NewPlayer", "secret");
            assertNotNull(account);
            assertEquals(2000L, store.loadProfile(account.uuid).tokenBalance());
            assertFalse(store.ensureInitialTokenBonus(account.uuid));
            assertEquals(2000L, store.loadProfile(account.uuid).tokenBalance());
        } finally {
            store.close();
        }
    }

    @Test
    void legacyAccountReceivesBonusOnceOnLoginAndPersistsIt() throws Exception {
        Path dir = Files.createTempDirectory("us-account-legacy-bonus");
        Path database = dir.resolve("accounts.db");
        AccountStore store = new AccountStore(database.toString());
        try {
            assertNull(store.createAccount("LegacyPlayer", "secret"));
            Account account = store.authenticate("LegacyPlayer", "secret");
            assertNotNull(account);
            PlayerProfile profile = store.loadProfile(account.uuid);
            profile.tokens = 500L;
            markInitialBonusUnreceived(store, account.uuid);
            store.saveProfile(account.uuid, profile);

            AccountAuthProvider auth = new AccountAuthProvider(store);
            assertNull(auth.login("LegacyPlayer", "secret", new Session(1)));
            assertEquals(2500L, profile.tokenBalance());
            assertNull(auth.login("LegacyPlayer", "secret", new Session(2)));
            assertEquals(2500L, profile.tokenBalance());
        } finally {
            store.close();
        }

        AccountStore reopened = new AccountStore(database.toString());
        try {
            Account account = reopened.authenticate("LegacyPlayer", "secret");
            assertNotNull(account);
            assertEquals(2500L, reopened.loadProfile(account.uuid).tokenBalance());
            assertNull(new AccountAuthProvider(reopened)
                    .login("LegacyPlayer", "secret", new Session(3)));
            assertEquals(2500L, reopened.loadProfile(account.uuid).tokenBalance());
        } finally {
            reopened.close();
        }
    }

    @SuppressWarnings("unchecked")
    private static void markInitialBonusUnreceived(AccountStore store, String uuid) throws Exception {
        java.lang.reflect.Field field = AccountStore.class.getDeclaredField("extraCache");
        field.setAccessible(true);
        Map<String, AccountExtra> cache = (Map<String, AccountExtra>) field.get(store);
        cache.get(uuid).receivedInitialTokenBonus = false;
    }
}
