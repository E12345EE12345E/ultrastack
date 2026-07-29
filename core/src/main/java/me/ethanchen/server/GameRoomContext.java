package me.ethanchen.server;

/** Interface implemented by GameRoom; defines the callbacks ServerGame uses. */
public interface GameRoomContext {
    void sendNetUpdates();
    void sendEndGame(GameEndInfo info);
    /** Called after the in-progress game has been torn down so the room can reseat spectators. */
    void onGameStopped();
}
