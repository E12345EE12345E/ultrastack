package me.ethanchen.server;

import me.ethanchen.network.packets.NetworkPacket;

/** Abstraction for sending packets to connected clients by connection ID. */
public interface PacketSender {
    void sendTCP(int connectionId, NetworkPacket packet);
    void sendUDP(int connectionId, NetworkPacket packet);
}
