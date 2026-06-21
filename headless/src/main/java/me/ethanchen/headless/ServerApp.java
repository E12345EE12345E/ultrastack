package me.ethanchen.headless;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.utils.Queue;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Server;

import me.ethanchen.game.board.Board;
import me.ethanchen.network.NetConfig;
import me.ethanchen.network.NetEndpoints;
import me.ethanchen.network.NetworkRegister;
import me.ethanchen.network.ServerNetworkListener;
import me.ethanchen.network.ServerPacketWrapper;
import me.ethanchen.network.packets.NetworkPacket;
import me.ethanchen.network.packets.c2s.*;
import me.ethanchen.network.packets.other.*;
import me.ethanchen.network.packets.s2c.*;
import me.ethanchen.util.TextSanitizer;

public class ServerApp extends ApplicationAdapter {
    // Network specific
    private Server server;
    private Queue<ServerPacketWrapper> actionQueue;

    // Logic
    private boolean allowConnections;
    private int serverLogCounter;
    private HashMap<Integer, Integer> playerIDs; // key: player id, value: connection id
    private HashMap<Integer, Player> players; // key: player id, value: name
    private ServerGame sg;
    private int t;
    
    @Override
    public void create() {
        actionQueue = new Queue<ServerPacketWrapper>();
        allowConnections = true;
        serverLogCounter = 300;
        playerIDs = new HashMap<Integer, Integer>();
        players = new HashMap<Integer, Player>();
        sg = new ServerGame(this);
        t = 0;
        server = NetEndpoints.createServer();
        try {
            server.bind(NetConfig.PORT, NetConfig.PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        server.addListener(new ServerNetworkListener(this.actionQueue));
        server.start();
        System.out.println("Server started successfully");
    }

    @Override
    public void render() { // 60 times per second
        while (actionQueue.notEmpty()) {
            ServerPacketWrapper wrapper = actionQueue.removeFirst();

            // ---------- Special ----------

            if (wrapper.packet instanceof DisconnectPacket) { // indicates a disconnect by server, manually created (not a sent packet)
                int k = getPlayerIDKey(wrapper.connectionID);
                
                if (k >= 0) {
                    playerIDs.remove(k);
                    players.remove(k);
                    System.out.println("Disconnect id: " + k + ", connection id: " + wrapper.connectionID);
                    if (sg.isInProgress()) {
                        sg.handleDisconnectedPlayer(k);
                    }
                }
            }

            // ---------- TCP ----------

            if (wrapper.packet instanceof JoinRequest) {
                if (!allowConnections) continue;
                JoinRequest req = (JoinRequest) wrapper.packet;
                System.out.println("JoinRequest from " + req.playerName);

                JoinResponse res = new JoinResponse();
                if (req.protocolVersion < NetworkRegister.PROTOCOL_VERSION) {
                    res.accepted = false;
                    res.playerId = -1;
                    res.reason = "outdated client";
                    wrapper.sendSafeTCP(res);
                    continue;
                } else if (req.protocolVersion > NetworkRegister.PROTOCOL_VERSION) {
                    res.accepted = false;
                    res.playerId = -1;
                    res.reason = "outdated server";
                    wrapper.sendSafeTCP(res);
                    continue;
                }

                boolean authenticated = false;
                if (req.credential == 1234) { // test
                    authenticated = true;
                }

                if (authenticated) {
                    int nextID = 0;
                    for (;playerIDs.containsKey(nextID);nextID++) {}
                    playerIDs.put(nextID, wrapper.connectionID);
                    players.put(nextID, new Player(req.playerName));

                    res.accepted = true;
                    res.playerId = nextID;
                    res.reason = "";
                } else {
                    res.accepted = false;
                    res.playerId = -1;
                    res.reason = "bad credential rejected";
                }
                wrapper.sendSafeTCP(res);
            }

            if (wrapper.packet instanceof TextMessageRequest) {
                TextMessageRequest req = (TextMessageRequest) wrapper.packet;
                int k = getPlayerIDKey(wrapper.connectionID);

                if (k >= 0) {
                    Player p = players.get(k);
                    System.out.println("Player " + p + " sent text message " + req.message);
                    TextMessageBroadcast b = new TextMessageBroadcast();
                    b.sender = p.username;
                    b.message = TextSanitizer.sanitizeChat(req.message);
                    broadcastToPlayersTCP(b);
                }
            }

            if (wrapper.packet instanceof MoveListRequest) {
                MoveListRequest req = (MoveListRequest) wrapper.packet;
                int k = getPlayerIDKey(wrapper.connectionID);
                if (k >= 0 && sg.isInProgress()) {
                    sg.applyMoves(k, req.ids, req.types);
                }
            }

            if (wrapper.packet instanceof StartGameRequest) {
                compressPlayerIDList();
                System.out.println(playerIDs);
                
                String[] currentNames = new String[playerIDs.size()];
                for (int i = 0; i < playerIDs.size(); i++) {
                    currentNames[i] = players.get(i).username;
                }

                for (int i=0; i<playerIDs.size(); i++) {
                    StartGameRequest req = (StartGameRequest) wrapper.packet;
                    sg.startGame(req.gamemode, playerIDs.size(), 5000);

                    StartGameBroadcast b = new StartGameBroadcast();
                    b.mode = req.gamemode;
                    b.boards = new Board.NetBoardFull[sg.getGame().getBoards().size()];
                    for (int a=0; a<sg.getGame().getBoards().size(); a++) {
                        b.boards[a] = sg.getGame().getBoards().get(a).convertToNetBoardFull();
                    }
                    b.totalPlayers = (byte) playerIDs.size();
                    b.playerID = (byte) i;
                    b.startTimeMS = System.currentTimeMillis() + 5000;
                    b.playerNames = currentNames;
                    broadcastToPlayerTCP(b, i);
                }
            }
        }
        t++;
        if (t % serverLogCounter == 0) {
            System.out.println("Connected:");
            System.out.println(playerIDs);
        }
        if (t % 10 == 0) {
            LobbyPlayerListBroadcast b = new LobbyPlayerListBroadcast();
            ArrayList<String> e = new ArrayList<String>();
            players.forEach((plr_id, plr) -> { e.add(plr.username); });
            b.playerNames = new String[e.size()];
            for (int i=0; i<e.size(); i++) { b.playerNames[i] = e.get(i); }
            broadcastToPlayersUDP(b);
        }
        sg.update();
    }

    public void sendNetUpdates() {
        // Collect particles once and build a shared broadcast object.
        ArrayList<NetParticle> particles = sg.getAndClearPendingParticles();
        ParticleBroadcast pb = null;
        if (particles != null && !particles.isEmpty()) {
            pb = new ParticleBroadcast();
            pb.particles = particles.toArray(new NetParticle[0]);
        }
        // Pre-build board snapshots shared across all per-player packets.
        Board.NetBoardLight[] boardSnapshots = new Board.NetBoardLight[sg.getGame().getBoards().size()];
        for (int a = 0; a < boardSnapshots.length; a++) {
            boardSnapshots[a] = sg.getGame().getBoards().get(a).convertToNetBoardLight();
        }
        for (int i = 0; i < playerIDs.size(); i++) {
            LightGameStateBroadcast b = new LightGameStateBroadcast();
            b.boards = boardSnapshots;
            b.ackMoveId = sg.getHighestMoveId(i);
            b.piecesPlaced = sg.getPiecesPlaced();
            b.holdAvailable = sg.computeHoldAvailable(i);
            switch (sg.getGame().getMode()) {
                case MULTIPLAYER_SCORE:
                    b.scoreMode = sg.getScoreModeData();
                    break;
                default:
                    break;
            }
            broadcastToPlayerUDP(b, i);
            if (pb != null) {
                broadcastToPlayerUDP(pb, i);
            }
        }
    }

    private int getPlayerIDKey(int connectionID) {
        Optional<Integer> key = playerIDs.entrySet().stream()
                    .filter(entry -> Objects.equals(entry.getValue(), connectionID)) // safe null check
                    .map(Map.Entry::getKey)
                    .findFirst();
        if (key.isPresent()) return key.get();
        else return -1;
    }

    private void compressPlayerIDList() {
        List<Integer> oldIds = new ArrayList<>(playerIDs.keySet());
        int n = oldIds.size();
        if (n <= 1) return; // 0 or 1 player is already dense
        Collections.shuffle(oldIds);
        HashMap<Integer, Integer> newPlayerIDs = new HashMap<>();
        HashMap<Integer, Player> newPlayers = new HashMap<>();
        for (int newId = 0; newId < n; newId++) {
            int oldId = oldIds.get(newId);
            newPlayerIDs.put(newId, playerIDs.get(oldId));
            newPlayers.put(newId, players.get(oldId));
        }
        playerIDs = newPlayerIDs;
        players = newPlayers;
    }

    protected void broadcastToPlayersTCP(NetworkPacket packet) {
        for (Connection conn : server.getConnections()) {
            if (conn == null || !conn.isConnected()) continue;
            if (playerIDs.containsValue(conn.getID())) {
                conn.sendTCP(packet);
            }
        }
    }

    protected void broadcastToPlayersUDP(NetworkPacket packet) {
        for (Connection conn : server.getConnections()) {
            if (conn == null || !conn.isConnected()) continue;
            if (playerIDs.containsValue(conn.getID())) {
                conn.sendUDP(packet);
            }
        }
    }

    protected void broadcastToPlayerTCP(NetworkPacket packet, int playerId) {
        Integer connectionId = playerIDs.get(playerId);
        if (connectionId == null) return;
        server.sendToTCP(connectionId, packet);
    }

    protected void broadcastToPlayerUDP(NetworkPacket packet, int playerId) {
        Integer connectionId = playerIDs.get(playerId);
        if (connectionId == null) return;
        server.sendToUDP(connectionId, packet);
    }

    /** Returns the username of the player with the given id, or null if not found. */
    public String getPlayerName(int playerId) {
        Player p = players.get(playerId);
        return (p != null) ? p.username : null;
    }
}
