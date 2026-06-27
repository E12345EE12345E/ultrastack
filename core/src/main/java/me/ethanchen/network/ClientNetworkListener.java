package me.ethanchen.network;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Queue;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;

import me.ethanchen.network.packets.NetworkPacket;
import me.ethanchen.network.packets.other.ConnectionEstablishedPacket;
import me.ethanchen.network.packets.other.DisconnectPacket;

public class ClientNetworkListener implements Listener {
    private final Queue<ClientPacketWrapper> rpackets;

    public ClientNetworkListener(Queue<ClientPacketWrapper> rpackets) {
        this.rpackets = rpackets;
    }

    @Override
    public void connected(Connection connection) {
        System.out.println("Connected to server");
        // Post ConnectionEstablishedPacket; the active screen decides what to send next.
        ClientPacketWrapper wrapper = new ClientPacketWrapper(new ConnectionEstablishedPacket(), connection);
        Gdx.app.postRunnable(() -> rpackets.addLast(wrapper));
    }

    @Override
    public void received(Connection connection, Object object) {
        if (object instanceof NetworkPacket) {
            ClientPacketWrapper wrapper = new ClientPacketWrapper((NetworkPacket) object, connection);
            Gdx.app.postRunnable(() -> rpackets.addLast(wrapper));
        }
    }

    @Override
    public void disconnected(Connection connection) {
        System.out.println("Disconnected");
        Gdx.app.postRunnable(() -> rpackets.addLast(new ClientPacketWrapper(new DisconnectPacket(), null)));
    }
}
