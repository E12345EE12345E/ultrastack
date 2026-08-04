package me.ethanchen.server;

import me.ethanchen.game.progression.PlayerProfile;

/**
 * Implemented by whatever backs persistent storage of player accounts (e.g. SQLite via
 * {@code AccountStore}), to load and save the character/artifact {@link PlayerProfile}
 * associated with an account.
 */
public interface ProfileStore {
    /** Returns the account's profile, or {@link PlayerProfile#defaultProfile()} if none exists yet. */
    PlayerProfile loadProfile(String accountUuid);

    /** Persists {@code profile} for the given account. No-op if the account is unknown. */
    void saveProfile(String accountUuid, PlayerProfile profile);
}
