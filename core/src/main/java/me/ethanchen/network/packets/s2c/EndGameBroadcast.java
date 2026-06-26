package me.ethanchen.network.packets.s2c;

import me.ethanchen.game.GameMode;
import me.ethanchen.network.packets.NetworkPacket;
import me.ethanchen.network.packets.s2c.gamemode.ScoreModeEndData;

public class EndGameBroadcast extends NetworkPacket {
    public GameMode mode;
    /** Player names ordered by player id. */
    public String[] playerNames;
    /** True if the players won; false if they lost. */
    public boolean win;
    /** Score-mode end data; null when mode is not MULTIPLAYER_SCORE. */
    public ScoreModeEndData scoreModeEnd;
    /** True if the game ended because a player disconnected rather than a natural conclusion. */
    public boolean disconnected;
}
