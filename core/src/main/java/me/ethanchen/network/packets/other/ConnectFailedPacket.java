package me.ethanchen.network.packets.other;

import me.ethanchen.network.packets.NetworkPacket;

/** Posted to the client packet queue when an auto-connect attempt fails. Never sent over the wire. */
public class ConnectFailedPacket extends NetworkPacket {
    public String reason;

    public ConnectFailedPacket() {}

    public ConnectFailedPacket(String reason) {
        this.reason = reason;
    }
}
