package me.ethanchen.network.packets.s2c;

import me.ethanchen.network.packets.NetworkPacket;

public class JoinResponse extends NetworkPacket {
    public boolean accepted;
    public int playerId;      // -1 if rejected
    public String reason;     // null or "" if ok
}
