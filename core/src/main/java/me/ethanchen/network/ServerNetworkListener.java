package me.ethanchen.network;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Queue;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;

import me.ethanchen.network.packets.NetworkPacket;
import me.ethanchen.network.packets.other.DisconnectPacket;

public class ServerNetworkListener implements Listener {
    private Queue<ServerPacketWrapper> actionQueue;

    public ServerNetworkListener(Queue<ServerPacketWrapper> aq) {
        this.actionQueue = aq;
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
            Gdx.app.postRunnable(new Runnable() {
                @Override
                public void run() {
                    actionQueue.addLast(wrapper);
                }
            });
        }
    }
    
    @Override
    public void disconnected(Connection connection) {
        Gdx.app.postRunnable(() -> {
            System.out.println("Player " + connection.getID() + " disconnected.");
            actionQueue.addLast(new ServerPacketWrapper(new DisconnectPacket(), connection.getID(), null));
            // Cleanup player data here
        });
    }
}
