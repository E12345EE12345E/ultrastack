package me.ethanchen.server;

import com.esotericsoftware.kryonet.Server;
import me.ethanchen.game.GameConstants;
import me.ethanchen.network.NetEndpoints;
import me.ethanchen.network.NetworkRegister;
import me.ethanchen.network.PacketDispatcher;
import me.ethanchen.network.ServerNetworkListener;
import me.ethanchen.network.ServerPacketWrapper;
import me.ethanchen.network.dto.RoomInfo;
import me.ethanchen.network.packets.NetworkPacket;
import me.ethanchen.network.packets.c2s.*;
import me.ethanchen.network.packets.s2c.*;
import me.ethanchen.network.packets.other.DisconnectPacket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ServerCore implements PacketSender, Runnable {

    private final Server kryoServer;
    private final ConcurrentLinkedQueue<ServerPacketWrapper> inbound = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<Integer, Session> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, GameRoom> rooms = new ConcurrentHashMap<>();
    // accountUuid -> connectionId of the session currently holding a room slot for that
    // account. Multiple connections may be logged into the same account simultaneously, but
    // only one of them may be in a room at a time; see claimAccountRoomSlot/releaseAccountRoomSlot.
    private final ConcurrentHashMap<String, Integer> accountRoomClaims = new ConcurrentHashMap<>();

    private final AuthProvider authProvider; // null = LAN mode
    private final ResultRecorder resultRecorder; // null = results not persisted (e.g. LAN mode)
    private final XpAwarder xpAwarder; // null = XP not awarded (e.g. LAN mode)
    private final long lanJoinCode;          // only relevant in LAN mode
    private final int roomIdDigits;
    private final Random rng = new Random();

    private volatile boolean running;
    private Thread loopThread;
    private int tickCount;
    private final PacketDispatcher<ServerPacketWrapper> dispatcher;

    /** Account-mode constructor. */
    public ServerCore(AuthProvider authProvider, ResultRecorder resultRecorder, XpAwarder xpAwarder, int roomIdDigits) {
        this.authProvider = authProvider;
        this.resultRecorder = resultRecorder;
        this.xpAwarder = xpAwarder;
        this.lanJoinCode = 0;
        this.roomIdDigits = roomIdDigits;
        this.kryoServer = NetEndpoints.createServer();
        this.dispatcher = buildDispatcher();
    }

    /** LAN-mode constructor (no auth, single implicit "LAN" room; results stay unpersisted).
     *  {@code lanJoinCode == 0} means no passcode is required to join. */
    public ServerCore(long lanJoinCode, int roomIdDigits) {
        this.authProvider = null;
        this.resultRecorder = null;
        this.xpAwarder = null;
        this.lanJoinCode = lanJoinCode;
        this.roomIdDigits = roomIdDigits;
        this.kryoServer = NetEndpoints.createServer();
        this.dispatcher = buildDispatcher();
    }

    /**
     * Builds the packet-class -> handler registry used by {@link #dispatch}. Built once at
     * construction time since which packet types are even valid depends on {@code authProvider}
     * (LAN mode vs. account mode) and never changes afterward.
     */
    private PacketDispatcher<ServerPacketWrapper> buildDispatcher() {
        PacketDispatcher<ServerPacketWrapper> d = new PacketDispatcher<>();
        d.on(DisconnectPacket.class, w -> handleDisconnect(w.connectionID));

        if (authProvider == null) {
            // ---- LAN mode: JoinRequest ----
            d.on(JoinRequest.class, w -> handleLanJoin(w, sessionFor(w)));
        } else {
            // ---- Account mode: auth + room packets ----
            d.on(LoginRequest.class, w -> handleLogin(w, sessionFor(w)));
            d.on(RegisterRequest.class, w -> handleRegister(w, sessionFor(w)));
            d.on(RoomListRequest.class, w -> handleRoomListRequest(w, sessionFor(w)));
            d.on(CreateRoomRequest.class, w -> handleCreateRoom(w, sessionFor(w)));
            d.on(JoinRoomRequest.class, w -> handleJoinRoom(w, sessionFor(w)));
            d.on(LeaveRoomRequest.class, w -> handleLeaveRoom(sessionFor(w)));
        }

        // ---- In-room packets (both modes) ----
        Consumer<ServerPacketWrapper> forward = w -> forwardToRoom(w, sessionFor(w));
        d.on(TextMessageRequest.class, forward);
        d.on(StartGameRequest.class, forward);
        d.on(MoveListRequest.class, forward);
        return d;
    }

    private Session sessionFor(ServerPacketWrapper w) {
        return sessions.get(w.connectionID);
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
                if (tickCount % GameConstants.ROOM_LIST_BROADCAST_INTERVAL_TICKS == 0) {
                    broadcastRoomList();
                }
            } catch (Exception e) {
                System.err.println("[ServerCore] Uncaught exception in loop: " + e);
                e.printStackTrace(System.err);
            }
            tickCount++;
            long elapsed = System.currentTimeMillis() - start;
            long sleep = GameConstants.TICK_MS - elapsed;
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
        if (sessionFor(w) == null) return; // shouldn't happen, but guard
        dispatcher.dispatch(w);
    }

    // -------------------------------------------------------------------------
    // LAN mode handlers
    // -------------------------------------------------------------------------

    private void handleLanJoin(ServerPacketWrapper w, Session session) {
        JoinRequest req = (JoinRequest) w.packet;
        System.out.println("[ServerCore] LAN JoinRequest from " + req.playerName);

        JoinResponse res = new JoinResponse();

        String versionError = protocolVersionMismatchReason(req.protocolVersion);
        if (versionError != null) {
            res.accepted = false;
            res.playerId = -1;
            res.reason = versionError;
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

        // Get-or-create the single LAN room. The host (first joiner to create the room) is
        // added inside the GameRoom constructor; tryAddMember below is a no-op for them that
        // simply returns their existing slot (always 0), so both host and later joiners get
        // their slot assigned/read through the same atomic path.
        GameRoom lanRoom = rooms.computeIfAbsent("LAN", id ->
                new GameRoom("LAN", this, w.connectionID, req.playerName));

        int slotId = lanRoom.tryAddMember(w.connectionID, req.playerName, session.accountUuid, GameConstants.MAX_PLAYERS);
        if (slotId < 0) {
            res.accepted = false;
            res.playerId = -1;
            res.reason = lanRoom.isInProgress() ? "game already in progress" : "room full";
            sendTCP(w.connectionID, res);
            return;
        }

        res.accepted = true;
        res.playerId = slotId;
        res.reason = "";
        sendTCP(w.connectionID, res);
        session.currentRoomId = "LAN";

        // Ensure room thread is running
        if (!lanRoom.isRunning()) {
            lanRoom.start();
        }
    }

    // -------------------------------------------------------------------------
    // Account mode handlers
    // -------------------------------------------------------------------------

    /**
     * Checks a client-supplied protocol version against {@link NetworkRegister#PROTOCOL_VERSION}.
     *
     * @return {@code null} if the versions match, otherwise a human-readable rejection reason
     *         suitable for a response packet's {@code reason} field.
     */
    private static String protocolVersionMismatchReason(byte clientVersion) {
        if (clientVersion < NetworkRegister.PROTOCOL_VERSION) return "outdated client";
        if (clientVersion > NetworkRegister.PROTOCOL_VERSION) return "outdated server";
        return null;
    }

    private void handleLogin(ServerPacketWrapper w, Session session) {
        LoginRequest req = (LoginRequest) w.packet;
        AuthResponse res = new AuthResponse();

        String versionError = protocolVersionMismatchReason(req.protocolVersion);
        if (versionError != null) {
            res.success = false;
            res.reason = versionError;
            sendTCP(w.connectionID, res);
            return;
        }

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

        String versionError = protocolVersionMismatchReason(req.protocolVersion);
        if (versionError != null) {
            res.success = false;
            res.reason = versionError;
            sendTCP(w.connectionID, res);
            return;
        }

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
        if (session.currentRoomId != null) {
            RoomJoinResponse res = new RoomJoinResponse();
            res.success = false;
            res.reason = "already in a room";
            sendTCP(w.connectionID, res);
            return;
        }
        if (!claimAccountRoomSlot(session)) {
            RoomJoinResponse res = new RoomJoinResponse();
            res.success = false;
            res.reason = "account already in a room on another connection";
            sendTCP(w.connectionID, res);
            return;
        }

        String roomId = generateRoomId();
        GameRoom room = new GameRoom(roomId, this, w.connectionID, session.username, session.accountUuid, resultRecorder, xpAwarder);
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
        if (!claimAccountRoomSlot(session)) {
            RoomJoinResponse res = new RoomJoinResponse();
            res.success = false;
            res.reason = "account already in a room on another connection";
            sendTCP(w.connectionID, res);
            return;
        }
        JoinRoomRequest req = (JoinRoomRequest) w.packet;
        GameRoom room = rooms.get(req.roomId);
        if (room == null) {
            releaseAccountRoomSlot(session);
            RoomJoinResponse res = new RoomJoinResponse();
            res.success = false;
            res.reason = "room not found";
            sendTCP(w.connectionID, res);
            return;
        }
        int slotId = room.tryAddMember(w.connectionID, session.username, session.accountUuid, GameConstants.MAX_PLAYERS);
        if (slotId < 0) {
            releaseAccountRoomSlot(session);
            RoomJoinResponse res = new RoomJoinResponse();
            res.success = false;
            res.reason = room.isInProgress() ? "game already in progress" : "room full";
            sendTCP(w.connectionID, res);
            return;
        }
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
        evictFromRoom(session.connectionId, session.currentRoomId);
        session.currentRoomId = null;
        releaseAccountRoomSlot(session);
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
            evictFromRoom(connectionId, session.currentRoomId);
        }
        releaseAccountRoomSlot(session);
    }

    /**
     * Removes {@code connectionId} from {@code roomId}, clears {@code currentRoomId} on any
     * sessions the room evicted as a side-effect (e.g. the host leaving a lobby), and tears
     * down the room if it's now empty. Shared by {@link #handleLeaveRoom} and
     * {@link #handleDisconnect}.
     */
    private void evictFromRoom(int connectionId, String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) return;
        List<Integer> evicted = room.handleDisconnect(connectionId);
        for (int evictedConnId : evicted) {
            Session evictedSession = sessions.get(evictedConnId);
            if (evictedSession != null) {
                evictedSession.currentRoomId = null;
                releaseAccountRoomSlot(evictedSession);
            }
        }
        if (room.isEmpty()) {
            rooms.remove(roomId);
            room.stop();
        }
    }

    // -------------------------------------------------------------------------
    // Per-account room-slot claims (prevents two connections logged into the same account
    // from being in a room simultaneously)
    // -------------------------------------------------------------------------

    /**
     * Atomically claims the "in a room" slot for {@code session}'s account.
     *
     * @return {@code true} if this connection now holds (or already held) the claim,
     *         {@code false} if a different connection logged into the same account already
     *         holds it.
     */
    private boolean claimAccountRoomSlot(Session session) {
        if (session.accountUuid == null) return true;
        Integer existing = accountRoomClaims.putIfAbsent(session.accountUuid, session.connectionId);
        return existing == null || existing.intValue() == session.connectionId;
    }

    /** Releases {@code session}'s account claim, but only if it's the one still holding it. */
    private void releaseAccountRoomSlot(Session session) {
        if (session.accountUuid != null) {
            accountRoomClaims.remove(session.accountUuid, session.connectionId);
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
        b.rooms = new RoomInfo[roomList.size()];
        for (int i = 0; i < roomList.size(); i++) {
            GameRoom r = roomList.get(i);
            RoomInfo info = new RoomInfo();
            info.roomId = r.roomId;
            info.hostName = r.getHostName();
            info.playerCount = r.getPlayerCount();
            info.inProgress = r.isInProgress();
            b.rooms[i] = info;
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
