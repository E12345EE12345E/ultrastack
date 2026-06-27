package me.ethanchen.network;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;

import java.util.function.Consumer;

import me.ethanchen.network.packets.NetworkPacket;
import me.ethanchen.network.packets.other.DisconnectPacket;

public class ServerNetworkListener implements Listener {
    private final Consumer<ServerPacketWrapper> onPacket;

    public ServerNetworkListener(Consumer<ServerPacketWrapper> onPacket) {
        this.onPacket = onPacket;
    }

    @Override
    public void connected(Connection connection) {
        System.out.println("Client connected: " + connection.getRemoteAddressTCP());
    }

    @Override
    public void received(Connection connection, Object object) {
        if (object instanceof NetworkPacket) {
            ServerPacketWrapper wrapper = new ServerPacketWrapper((NetworkPacket) object, connection.getID(), connection);
            System.out.println(wrapper);
            onPacket.accept(wrapper);
        }
    }

    @Override
    public void disconnected(Connection connection) {
        System.out.println("Player " + connection.getID() + " disconnected.");
        onPacket.accept(new ServerPacketWrapper(new DisconnectPacket(), connection.getID(), null));
    }
}
