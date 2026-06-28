package me.ethanchen.server;

/** Represents a connected (but possibly not yet authenticated) client. */
public class Session {
    public final int connectionId;
    public volatile String username;       // null until logged in
    public volatile String accountUuid;    // null until logged in (LAN: set to playerName)
    public volatile String currentRoomId;  // null if not in a room
    public volatile boolean authenticated; // false until JoinResponse/AuthResponse accepted

    public Session(int connectionId) {
        this.connectionId = connectionId;
    }
}
