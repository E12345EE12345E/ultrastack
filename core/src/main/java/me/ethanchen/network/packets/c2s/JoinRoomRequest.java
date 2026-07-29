package me.ethanchen.network.packets.c2s;

import me.ethanchen.network.packets.NetworkPacket;

public class JoinRoomRequest extends NetworkPacket {
    public String roomId;
    /** Number of local players this client wants seated (keyboard/controllers). */
    public byte localPlayers = 1;
}
