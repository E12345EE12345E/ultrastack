package me.ethanchen.network;

import java.util.Objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Queue;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;

import me.ethanchen.network.packets.NetworkPacket;
import me.ethanchen.network.packets.c2s.JoinRequest;
import me.ethanchen.network.packets.other.DisconnectPacket;
import me.ethanchen.network.packets.s2c.JoinResponse;

public class ClientNetworkListener implements Listener {
    private Queue<ClientPacketWrapper> rpackets;
    private volatile String playerName;
    private volatile long credential;

    public ClientNetworkListener(Queue<ClientPacketWrapper> rpackets, String playerName, long credential) {
        this.rpackets = rpackets;
        this.playerName = playerName;
        this.credential = credential;
    }

    public void setPlayerName(String newName) {
        this.playerName = newName;
    }

    public void setCredential(long newCredential) {
        this.credential = newCredential;
    }

    @Override
    public void connected(Connection connection) {
        System.out.println("Connected to server");
        // Joins are the only thing handled directly by this class
        JoinRequest req = new JoinRequest();
        req.playerName = Objects.requireNonNullElse(playerName, "NULL_USER");
        req.credential = credential;
        connection.sendTCP(req);
    }

    @Override
    public void received(Connection connection, Object object) {
        // Joins are the only thing handled directly by this class
        if (object instanceof JoinResponse) {
            JoinResponse res = (JoinResponse) object;
            System.out.println("JoinResponse accepted=" + res.accepted + " playerId=" + res.playerId + " reason=" + res.reason);
            if (!res.accepted) {
                connection.close();
            }
        }
        // Network packets go to client app (including join responses)
        if (object instanceof NetworkPacket) {
            ClientPacketWrapper wrapper = new ClientPacketWrapper((NetworkPacket) object, connection);
            Gdx.app.postRunnable(new Runnable() {
                @Override
                public void run() {
                    rpackets.addLast(wrapper);
                }
            });
        }
    }

    @Override
    public void disconnected(Connection connection) {
        System.out.println("Disconnected");
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                rpackets.addLast(new ClientPacketWrapper(new DisconnectPacket(), null));
            }
        });
    }
}
