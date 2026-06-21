package me.ethanchen.network.packets.s2c;

import me.ethanchen.network.packets.NetworkPacket;
import me.ethanchen.network.packets.s2c.gamemode.ScoreModeEndData;

/**
 * Sent via TCP by the server when the game ends (all players' pieces exploded).
 * Clients fade to black then switch to EndGameScreen.
 */
public class EndGameBroadcast extends NetworkPacket {
    /** All player names ordered by player id. */
    public String[] playerNames;
    /** True = players won (unused currently; false = loss when pieces explode). */
    public boolean win;
    /** Score-mode end data, null in other modes. */
    public ScoreModeEndData scoreModeEnd;
}
