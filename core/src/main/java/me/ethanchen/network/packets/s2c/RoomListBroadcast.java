package me.ethanchen.network.packets.s2c;

import me.ethanchen.network.dto.RoomInfo;
import me.ethanchen.network.packets.NetworkPacket;

public class RoomListBroadcast extends NetworkPacket {
    public RoomInfo[] rooms;
}
