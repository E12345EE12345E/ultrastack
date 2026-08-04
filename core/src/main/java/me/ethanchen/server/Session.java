package me.ethanchen.server;

import me.ethanchen.game.progression.PlayerProfile;

/** Represents a connected (but possibly not yet authenticated) client. */
public class Session {
    public final int connectionId;
    public volatile String username;       // null until logged in
    public volatile String accountUuid;    // null until logged in (LAN: set to playerName)
    public volatile String currentRoomId;  // null if not in a room
    public volatile boolean authenticated; // false until JoinResponse/AuthResponse accepted

    /** Cached character/artifact profile for this session; null until loaded/synthesized. */
    public volatile PlayerProfile profile;
    /** True in LAN mode: {@link #profile} is session-only and never persisted (implementation.md, Part 5). */
    public volatile boolean profileReadOnly;

    public Session(int connectionId) {
        this.connectionId = connectionId;
    }
}
