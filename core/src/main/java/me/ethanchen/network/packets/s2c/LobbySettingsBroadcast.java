package me.ethanchen.network.packets.s2c;

import me.ethanchen.game.GameMode;
import me.ethanchen.network.packets.NetworkPacket;

/**
 * Broadcast (or sent to a newly joined member) with the room's pending lobby settings so
 * clients can enable/disable the character loadout UI before the game starts.
 */
public class LobbySettingsBroadcast extends NetworkPacket {
    public GameMode gamemode;
    public int pveLevelId;
    public int pveDifficulty;
}
