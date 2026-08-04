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
    private volatile boolean attached = true;

    public ClientNetworkListener(Queue<ClientPacketWrapper> rpackets) {
        this.rpackets = rpackets;
    }

    /**
     * Permanently stops this listener from reporting anything. Called when the client it
     * belongs to is being retired, so that a shutdown we asked for is not mistaken for a
     * lost connection and a connection nobody wants any more cannot drive the UI.
     */
    public void detach() {
        attached = false;
    }

    @Override
    public void connected(Connection connection) {
        if (!attached) return;
        System.out.println("Connected to server");
        // Post ConnectionEstablishedPacket; the active screen decides what to send next.
        ClientPacketWrapper wrapper = new ClientPacketWrapper(new ConnectionEstablishedPacket(), connection);
        Gdx.app.postRunnable(() -> {
            if (attached) rpackets.addLast(wrapper);
        });
    }

    @Override
    public void received(Connection connection, Object object) {
        if (!attached) return;
        if (object instanceof NetworkPacket) {
            ClientPacketWrapper wrapper = new ClientPacketWrapper((NetworkPacket) object, connection);
            Gdx.app.postRunnable(() -> {
                if (attached) rpackets.addLast(wrapper);
            });
        }
    }

    @Override
    public void disconnected(Connection connection) {
        if (!attached) return;
        System.out.println("Disconnected");
        Gdx.app.postRunnable(() -> {
            if (attached) rpackets.addLast(new ClientPacketWrapper(new DisconnectPacket(), null));
        });
    }
}
