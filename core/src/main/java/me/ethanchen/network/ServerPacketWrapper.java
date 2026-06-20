package me.ethanchen.network;

import com.esotericsoftware.kryonet.Connection;

import me.ethanchen.network.packets.NetworkPacket;

public class ServerPacketWrapper extends PacketWrapper {
    public final int connectionID;

    public ServerPacketWrapper(NetworkPacket packet, int id, Connection connection) {
        super(packet, connection);
        this.connectionID = id;
    }

    @Override
    public String toString() {
        return "ServerPacketWrapper[id=" + connectionID + "]: " + packet;
    }
}
