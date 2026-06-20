package me.ethanchen.network;

import com.esotericsoftware.kryonet.Connection;

import me.ethanchen.network.packets.NetworkPacket;

public class PacketWrapper {
    public final NetworkPacket packet;
    public final Connection connection;

    public PacketWrapper(NetworkPacket p, Connection c) {
        packet = p;
        connection = c;
    }

    public int sendSafeTCP(Object o) {
        if (connection == null || !connection.isConnected()) return -1;
        return connection.sendTCP(o);
    }

    public int sendSafeUDP(Object o) {
        if (connection == null || !connection.isConnected()) return -1;
        return connection.sendUDP(o);
    }
}
