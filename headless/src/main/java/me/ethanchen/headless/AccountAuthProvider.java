package me.ethanchen.headless;

import me.ethanchen.server.AuthProvider;
import me.ethanchen.server.Session;
import me.ethanchen.util.TextSanitizer;

public class AccountAuthProvider implements AuthProvider {
    private final AccountStore store;

    public AccountAuthProvider(AccountStore store) {
        this.store = store;
    }

    @Override
    public String register(String username, String passcode) {
        if (username == null || username.isBlank()) return "username cannot be blank";
        if (passcode == null || passcode.isBlank()) return "passcode cannot be blank";
        String key = username.trim().toLowerCase();
        if (key.length() < TextSanitizer.MIN_REGISTER_USERNAME_LENGTH) {
            return "username must be at least " + TextSanitizer.MIN_REGISTER_USERNAME_LENGTH + " characters";
        }
        return store.createAccount(key, passcode);
    }

    @Override
    public String login(String username, String passcode, Session session) {
        if (username == null || passcode == null) return "invalid credentials";
        Account acct = store.authenticate(username, passcode);
        if (acct == null) return "invalid username or passcode";
        session.username = acct.username;
        session.accountUuid = acct.uuid;
        session.authenticated = true;
        return null;
    }
}
