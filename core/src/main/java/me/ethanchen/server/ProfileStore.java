package me.ethanchen.server;

import me.ethanchen.game.progression.PlayerProfile;

/**
 * Implemented by whatever backs persistent storage of player accounts (e.g. SQLite via
 * {@code AccountStore}), to load and save the character/artifact {@link PlayerProfile}
 * associated with an account.
 *
 * <p>{@link #loadProfile} must return a stable live instance per account for the lifetime of the
 * process (cache after first read). {@code ServerCore} keeps {@code session.profile} as that
 * reference while {@code GameRoom} also loads via this store for grants/loadouts -- returning a
 * fresh deserialized copy each time leaves the session stale and breaks fusion.
 */
public interface ProfileStore {
    /**
     * Returns the account's live profile, or {@link PlayerProfile#defaultProfile()} if none exists
     * yet. Same account uuid must yield the same instance until process exit / explicit replace
     * via {@link #saveProfile}.
     */
    PlayerProfile loadProfile(String accountUuid);

    /** Persists {@code profile} for the given account and installs it as the live cached instance. No-op if the account is unknown. */
    void saveProfile(String accountUuid, PlayerProfile profile);
}
