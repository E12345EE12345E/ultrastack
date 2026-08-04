package me.ethanchen.server;

import java.util.concurrent.ConcurrentHashMap;

import me.ethanchen.game.progression.LanProfileFactory;
import me.ethanchen.game.progression.PlayerProfile;

/**
 * In-memory {@link ProfileStore} used for LAN sessions: every character unlocked with two
 * pre-generated artifacts (implementation.md, Part 5), loadout changes are reflected live so
 * {@link GameRoom} sees them at game start, but nothing survives past this server process --
 * there is no backing disk store, and fusion/acquisition are additionally blocked at the
 * request-handling layer via {@code Session.profileReadOnly}.
 */
final class LanProfileStore implements ProfileStore {
    private final ConcurrentHashMap<String, PlayerProfile> profiles = new ConcurrentHashMap<>();

    @Override
    public PlayerProfile loadProfile(String accountUuid) {
        return profiles.computeIfAbsent(accountUuid, k -> LanProfileFactory.create());
    }

    @Override
    public void saveProfile(String accountUuid, PlayerProfile profile) {
        profiles.put(accountUuid, profile);
    }
}
