package me.ethanchen.network.packets.s2c;

import me.ethanchen.network.packets.NetworkPacket;

public class LobbyPlayerListBroadcast extends NetworkPacket {
    public String[] playerNames;
}
