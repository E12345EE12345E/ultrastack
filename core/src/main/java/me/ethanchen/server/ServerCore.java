package me.ethanchen.server;

import com.esotericsoftware.kryonet.Server;
import me.ethanchen.network.NetEndpoints;
import me.ethanchen.network.NetworkRegister;
import me.ethanchen.network.ServerNetworkListener;
import me.ethanchen.network.ServerPacketWrapper;
import me.ethanchen.network.packets.NetworkPacket;
import me.ethanchen.network.packets.c2s.*;
import me.ethanchen.network.packets.s2c.*;
import me.ethanchen.network.packets.other.DisconnectPacket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ServerCore implements PacketSender, Runnable {

    private final Server kryoServer;
    private final ConcurrentLinkedQueue<ServerPacketWrapper> inbound = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<Integer, Session> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, GameRoom> rooms = new ConcurrentHashMap<>();

    private final AuthProvider authProvider; // null = LAN mode
    private final long lanJoinCode;          // only relevant in LAN mode
    private final int roomIdDigits;
    private final Random rng = new Random();

    private volatile boolean running;
    private Thread loopThread;
    private int tickCount;

    private static final int ROOM_LIST_BROADCAST_INTERVAL = 300; // ~5s at 60Hz

    /** Account-mode constructor. */
    public ServerCore(AuthProvider authProvider, int roomIdDigits) {
        this.authProvider = authProvider;
        this.lanJoinCode = 0;
        this.roomIdDigits = roomIdDigits;
        this.kryoServer = NetEndpoints.createServer();
    }

    /** LAN-mode constructor (no auth, single implicit "LAN" room). */
    public ServerCore(long lanJoinCode, int roomIdDigits) {
        this.authProvider = null;
        this.lanJoinCode = lanJoinCode;
        this.roomIdDigits = roomIdDigits;
        this.kryoServer = NetEndpoints.createServer();
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void start(int port) throws IOException {
        kryoServer.addListener(new ServerNetworkListener(this::onPacket));
        kryoServer.bind(port, port);
        kryoServer.start();
        running = true;
        loopThread = new Thread(this, "server-core-loop");
        loopThread.setDaemon(true);
        loopThread.start();
        System.out.println("[ServerCore] Started on port " + port);
    }

    public void stop() {
        running = false;
        for (GameRoom room : rooms.values()) room.stop();
        kryoServer.stop();
    }

    // -------------------------------------------------------------------------
    // Network thread callback
    // -------------------------------------------------------------------------

    private void onPacket(ServerPacketWrapper wrapper) {
        // Lazily create a Session record on first packet from a connection.
        sessions.computeIfAbsent(wrapper.connectionID, Session::new);
        inbound.add(wrapper);
    }

    // -------------------------------------------------------------------------
    // Main loop
    // -------------------------------------------------------------------------

    @Override
    public void run() {
        while (running) {
            long start = System.currentTimeMillis();
            try {
                drainInbound();
                if (tickCount % ROOM_LIST_BROADCAST_INTERVAL == 0) {
                    broadcastRoomList();
                }
            } catch (Exception e) {
                System.err.println("[ServerCore] Uncaught exception in loop: " + e);
                e.printStackTrace(System.err);
            }
            tickCount++;
            long elapsed = System.currentTimeMillis() - start;
            long sleep = 16 - elapsed;
            if (sleep > 0) {
                try { Thread.sleep(sleep); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }
    }

    private void drainInbound() {
        ServerPacketWrapper w;
        while ((w = inbound.poll()) != null) {
            dispatch(w);
        }
    }

    // -------------------------------------------------------------------------
    // Dispatch
    // -------------------------------------------------------------------------

    private void dispatch(ServerPacketWrapper w) {
        Session session = sessions.get(w.connectionID);
        if (session == null) return; // shouldn't happen, but guard

        // ---- Disconnect ----
        if (w.packet instanceof DisconnectPacket) {
            handleDisconnect(w.connectionID);
            return;
        }

        // ---- LAN mode: JoinRequest ----
        if (authProvider == null) {
            if (w.packet instanceof JoinRequest) {
                handleLanJoin(w, session);
                return;
            }
        }

        // ---- Account mode: auth packets ----
        if (authProvider != null) {
            if (w.packet instanceof LoginRequest) {
                handleLogin(w, session);
                return;
            }
            if (w.packet instanceof RegisterRequest) {
                handleRegister(w, session);
                return;
            }
            if (w.packet instanceof RoomListRequest) {
                handleRoomListRequest(w, session);
                return;
            }
            if (w.packet instanceof CreateRoomRequest) {
                handleCreateRoom(w, session);
                return;
            }
            if (w.packet instanceof JoinRoomRequest) {
                handleJoinRoom(w, session);
                return;
            }
            if (w.packet instanceof LeaveRoomRequest) {
                handleLeaveRoom(session);
                return;
            }
        }

        // ---- In-room packets (both modes) ----
        if (w.packet instanceof TextMessageRequest
                || w.packet instanceof StartGameRequest
                || w.packet instanceof MoveListRequest) {
            forwardToRoom(w, session);
        }
    }

    // -------------------------------------------------------------------------
    // LAN mode handlers
    // -------------------------------------------------------------------------

    private void handleLanJoin(ServerPacketWrapper w, Session session) {
        JoinRequest req = (JoinRequest) w.packet;
        System.out.println("[ServerCore] LAN JoinRequest from " + req.playerName);

        JoinResponse res = new JoinResponse();

        if (req.protocolVersion < NetworkRegister.PROTOCOL_VERSION) {
            res.accepted = false;
            res.playerId = -1;
            res.reason = "outdated client";
            sendTCP(w.connectionID, res);
            return;
        } else if (req.protocolVersion > NetworkRegister.PROTOCOL_VERSION) {
            res.accepted = false;
            res.playerId = -1;
            res.reason = "outdated server";
            sendTCP(w.connectionID, res);
            return;
        }

        if (req.credential != lanJoinCode) {
            res.accepted = false;
            res.playerId = -1;
            res.reason = "bad credential";
            sendTCP(w.connectionID, res);
            return;
        }

        // Set session fields
        session.username = req.playerName;
        session.accountUuid = req.playerName; // LAN: uuid = name
        session.authenticated = true;

        // Get-or-create the single LAN room
        GameRoom lanRoom = rooms.computeIfAbsent("LAN", id ->
                new GameRoom("LAN", this, w.connectionID, req.playerName));

        // Assign a slot id (the room's current member count before adding)
        int slotId = lanRoom.getPlayerCount();

        res.accepted = true;
        res.playerId = slotId;
        res.reason = "";
        sendTCP(w.connectionID, res);

        // If this is not the host (host was added in computeIfAbsent), add them now
        if (slotId > 0) {
            lanRoom.addMember(w.connectionID, req.playerName);
        }
        session.currentRoomId = "LAN";

        // Ensure room thread is running
        if (!lanRoom.isRunning()) {
            lanRoom.start();
        }
    }

    // -------------------------------------------------------------------------
    // Account mode handlers
    // -------------------------------------------------------------------------

    private void handleLogin(ServerPacketWrapper w, Session session) {
        LoginRequest req = (LoginRequest) w.packet;
        AuthResponse res = new AuthResponse();
        String error = authProvider.login(req.username, req.passcode, session);
        if (error == null) {
            res.success = true;
            res.reason = "";
            res.accountUuid = session.accountUuid;
            session.username = req.username;
            session.authenticated = true;
        } else {
            res.success = false;
            res.reason = error;
        }
        sendTCP(w.connectionID, res);
    }

    private void handleRegister(ServerPacketWrapper w, Session session) {
        RegisterRequest req = (RegisterRequest) w.packet;
        AuthResponse res = new AuthResponse();
        String error = authProvider.register(req.username, req.passcode);
        if (error == null) {
            // Registration succeeded — also authenticate the session so the player
            // can immediately use room operations without a separate login step.
            String loginError = authProvider.login(req.username, req.passcode, session);
            if (loginError == null) {
                res.success = true;
                res.reason = "";
                res.accountUuid = session.accountUuid;
            } else {
                // Account was created but immediate login failed (shouldn't happen).
                res.success = false;
                res.reason = "registered but login failed: " + loginError;
            }
        } else {
            res.success = false;
            res.reason = error;
        }
        sendTCP(w.connectionID, res);
    }

    private void handleRoomListRequest(ServerPacketWrapper w, Session session) {
        if (!session.authenticated) return;
        sendTCP(w.connectionID, buildRoomListBroadcast());
    }

    private void handleCreateRoom(ServerPacketWrapper w, Session session) {
        if (!session.authenticated) return;
        if (session.currentRoomId != null) return; // already in a room

        String roomId = generateRoomId();
        GameRoom room = new GameRoom(roomId, this, w.connectionID, session.username);
        rooms.put(roomId, room);
        session.currentRoomId = roomId;
        room.start();

        RoomJoinResponse res = new RoomJoinResponse();
        res.success = true;
        res.reason = "";
        res.roomId = roomId;
        res.isHost = true;
        sendTCP(w.connectionID, res);
    }

    private void handleJoinRoom(ServerPacketWrapper w, Session session) {
        if (!session.authenticated) return;
        if (session.currentRoomId != null) {
            RoomJoinResponse res = new RoomJoinResponse();
            res.success = false;
            res.reason = "already in a room";
            sendTCP(w.connectionID, res);
            return;
        }
        JoinRoomRequest req = (JoinRoomRequest) w.packet;
        GameRoom room = rooms.get(req.roomId);
        if (room == null) {
            RoomJoinResponse res = new RoomJoinResponse();
            res.success = false;
            res.reason = "room not found";
            sendTCP(w.connectionID, res);
            return;
        }
        if (room.isInProgress()) {
            RoomJoinResponse res = new RoomJoinResponse();
            res.success = false;
            res.reason = "game already in progress";
            sendTCP(w.connectionID, res);
            return;
        }
        room.addMember(w.connectionID, session.username);
        session.currentRoomId = req.roomId;

        RoomJoinResponse res = new RoomJoinResponse();
        res.success = true;
        res.reason = "";
        res.roomId = req.roomId;
        res.isHost = false;
        sendTCP(w.connectionID, res);
    }

    private void handleLeaveRoom(Session session) {
        if (session.currentRoomId == null) return;
        String roomId = session.currentRoomId;
        GameRoom room = rooms.get(roomId);
        if (room != null) {
            List<Integer> evicted = room.handleDisconnect(session.connectionId);
            for (int evictedConnId : evicted) {
                Session evictedSession = sessions.get(evictedConnId);
                if (evictedSession != null) evictedSession.currentRoomId = null;
            }
            if (room.isEmpty()) {
                rooms.remove(roomId);
                room.stop();
            }
        }
        session.currentRoomId = null;
    }

    // -------------------------------------------------------------------------
    // Shared / both-mode handlers
    // -------------------------------------------------------------------------

    private void handleDisconnect(int connectionId) {
        Session session = sessions.remove(connectionId);
        if (session == null) return;
        System.out.println("[ServerCore] Disconnected: connId=" + connectionId
                + " user=" + session.username);
        if (session.currentRoomId != null) {
            String roomId = session.currentRoomId;
            GameRoom room = rooms.get(roomId);
            if (room != null) {
                List<Integer> evicted = room.handleDisconnect(connectionId);
                for (int evictedConnId : evicted) {
                    Session evictedSession = sessions.get(evictedConnId);
                    if (evictedSession != null) evictedSession.currentRoomId = null;
                }
                if (room.isEmpty()) {
                    rooms.remove(roomId);
                    room.stop();
                }
            }
        }
    }

    private void forwardToRoom(ServerPacketWrapper w, Session session) {
        if (session.currentRoomId == null) return;
        GameRoom room = rooms.get(session.currentRoomId);
        if (room == null) return;
        room.handlePacket(w);
    }

    // -------------------------------------------------------------------------
    // Room-list broadcast
    // -------------------------------------------------------------------------

    private void broadcastRoomList() {
        if (authProvider == null) return; // LAN mode doesn't use room list
        RoomListBroadcast broadcast = buildRoomListBroadcast();
        for (Session s : sessions.values()) {
            if (s.authenticated && s.currentRoomId == null) {
                sendTCP(s.connectionId, broadcast);
            }
        }
    }

    private RoomListBroadcast buildRoomListBroadcast() {
        List<GameRoom> roomList = new ArrayList<>(rooms.values());
        RoomListBroadcast b = new RoomListBroadcast();
        b.roomIds = new String[roomList.size()];
        b.hostNames = new String[roomList.size()];
        b.playerCounts = new int[roomList.size()];
        b.inProgress = new boolean[roomList.size()];
        for (int i = 0; i < roomList.size(); i++) {
            GameRoom r = roomList.get(i);
            b.roomIds[i] = r.roomId;
            b.hostNames[i] = r.getHostName();
            b.playerCounts[i] = r.getPlayerCount();
            b.inProgress[i] = r.isInProgress();
        }
        return b;
    }

    // -------------------------------------------------------------------------
    // Room ID generation
    // -------------------------------------------------------------------------

    private String generateRoomId() {
        String id;
        do {
            int max = (int) Math.pow(10, roomIdDigits);
            int n = rng.nextInt(max);
            id = String.format("%0" + roomIdDigits + "d", n);
        } while (rooms.containsKey(id));
        return id;
    }

    // -------------------------------------------------------------------------
    // PacketSender implementation
    // -------------------------------------------------------------------------

    @Override
    public void sendTCP(int connectionId, NetworkPacket packet) {
        kryoServer.sendToTCP(connectionId, packet);
    }

    @Override
    public void sendUDP(int connectionId, NetworkPacket packet) {
        kryoServer.sendToUDP(connectionId, packet);
    }

    public void broadcastTCP(Collection<Integer> connIds, NetworkPacket packet) {
        for (int id : connIds) {
            kryoServer.sendToTCP(id, packet);
        }
    }

    public void broadcastUDP(Collection<Integer> connIds, NetworkPacket packet) {
        for (int id : connIds) {
            kryoServer.sendToUDP(id, packet);
        }
    }
}
