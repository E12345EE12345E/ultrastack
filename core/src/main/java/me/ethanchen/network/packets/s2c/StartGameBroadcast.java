package me.ethanchen.network.packets.s2c;

import me.ethanchen.game.GameMode;
import me.ethanchen.network.dto.NetBoardFull;
import me.ethanchen.network.packets.NetworkPacket;

public class StartGameBroadcast extends NetworkPacket {
    public GameMode mode;
    public NetBoardFull[] boards;
    public byte totalPlayers;
    public byte playerId; // id is resent since it might have changed
    public long startTimeMS;
    public String[] playerNames;
}
