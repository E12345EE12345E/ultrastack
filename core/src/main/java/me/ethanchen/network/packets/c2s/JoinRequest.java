package me.ethanchen.network.packets.c2s;

import me.ethanchen.network.NetworkRegister;
import me.ethanchen.network.packets.NetworkPacket;

public class JoinRequest extends NetworkPacket {
    public String playerName;
    public long credential;
    public byte protocolVersion = NetworkRegister.PROTOCOL_VERSION;
}