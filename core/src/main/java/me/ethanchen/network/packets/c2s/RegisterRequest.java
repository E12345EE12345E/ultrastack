package me.ethanchen.network.packets.c2s;

import me.ethanchen.network.NetworkRegister;
import me.ethanchen.network.packets.NetworkPacket;

public class RegisterRequest extends NetworkPacket {
    public String username;
    public String passcode; // sent plaintext; stored as PBKDF2 hash server-side
    public byte protocolVersion = NetworkRegister.PROTOCOL_VERSION;
}
