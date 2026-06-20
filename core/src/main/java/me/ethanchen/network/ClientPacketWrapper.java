package me.ethanchen.network;

import com.esotericsoftware.kryonet.Connection;

import me.ethanchen.network.packets.NetworkPacket;

public class ClientPacketWrapper extends PacketWrapper {
    public ClientPacketWrapper(NetworkPacket packet, Connection connection) {
        super(packet, connection);
    }

    @Override
    public String toString() {
        return "ClientPacketWrapper: " + packet;
    }
}
