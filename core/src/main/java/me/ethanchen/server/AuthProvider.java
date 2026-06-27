package me.ethanchen.server;

/** Pluggable authentication: null means LAN/no-auth mode. */
public interface AuthProvider {
    /** Returns null on success, error string on failure. */
    String register(String username, String passcode);

    /** Returns null on success (sets accountUuid on session), error string on failure. */
    String login(String username, String passcode, Session session);
}
