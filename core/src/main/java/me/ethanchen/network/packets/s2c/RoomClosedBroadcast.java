package me.ethanchen.network.packets.s2c;

import me.ethanchen.network.packets.NetworkPacket;

/** Sent to all non-host lobby members when the host leaves the room. */
public class RoomClosedBroadcast extends NetworkPacket {
    public String reason;
}
