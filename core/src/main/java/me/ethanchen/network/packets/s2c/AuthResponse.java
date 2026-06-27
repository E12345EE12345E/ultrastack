package me.ethanchen.network.packets.s2c;

import me.ethanchen.network.packets.NetworkPacket;

public class AuthResponse extends NetworkPacket {
    public boolean success;
    public String reason;      // null/"" if ok, error message if not
    public String accountUuid; // null if not accepted
}
