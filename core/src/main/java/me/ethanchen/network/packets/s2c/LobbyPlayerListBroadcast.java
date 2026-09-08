package me.ethanchen.network.packets.s2c;

import me.ethanchen.network.dto.LobbyPlayerInfo;
import me.ethanchen.network.packets.NetworkPacket;

public class LobbyPlayerListBroadcast extends NetworkPacket {
    /** Active seats in slot order, then spectators in join order. */
    public LobbyPlayerInfo[] players = new LobbyPlayerInfo[0];
}
