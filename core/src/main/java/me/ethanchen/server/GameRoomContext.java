package me.ethanchen.server;

/** Interface implemented by GameRoom; defines the callbacks ServerGame uses. */
public interface GameRoomContext {
    void sendNetUpdates();
    void sendEndGame(GameEndInfo info);
}
