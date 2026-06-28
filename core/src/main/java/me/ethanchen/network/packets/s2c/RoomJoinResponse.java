package me.ethanchen.network.packets.s2c;

import me.ethanchen.network.packets.NetworkPacket;

public class RoomJoinResponse extends NetworkPacket {
    public boolean success;
    public String reason;
    public String roomId;
    public boolean isHost;
}
