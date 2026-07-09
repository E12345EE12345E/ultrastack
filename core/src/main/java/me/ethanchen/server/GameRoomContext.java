package me.ethanchen.server;

import me.ethanchen.network.packets.s2c.gamemode.ScoreModeEndData;

/** Interface implemented by GameRoom; defines the callbacks ServerGame uses. */
public interface GameRoomContext {
    void sendNetUpdates();
    void sendEndGame(boolean win, ScoreModeEndData scoreEnd, boolean disconnected);
}
