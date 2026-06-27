package me.ethanchen.network.packets.s2c;

import me.ethanchen.network.packets.NetworkPacket;

public class RoomListBroadcast extends NetworkPacket {
    public String[] roomIds;
    public String[] hostNames;
    public int[] playerCounts;
    public boolean[] inProgress;
}
