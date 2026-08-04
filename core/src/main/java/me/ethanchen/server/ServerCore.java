package me.ethanchen.server;

import com.esotericsoftware.kryonet.Server;
import me.ethanchen.game.GameConstants;
import me.ethanchen.game.progression.Artifact;
import me.ethanchen.game.progression.CharacterRegistry;
import me.ethanchen.game.progression.PlayerProfile;
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
    private final ProfileStore profileStore; // null = LAN mode (profiles are session-only)
    private final long lanJoinCode;          // only relevant in LAN mode
    private final int roomIdDigits;
    private final Random rng = new Random();

    private volatile boolean running;
    private Thread loopThread;
    private int tickCount;
    private final PacketDispatcher<ServerPacketWrapper> dispatcher;

    /** Account-mode constructor. */
    public ServerCore(AuthProvider authProvider, ResultRecorder resultRecorder, XpAwarder xpAwarder,
                       ProfileStore profileStore, int roomIdDigits) {
        this.authProvider = authProvider;
        this.resultRecorder = resultRecorder;
        this.xpAwarder = xpAwarder;
        this.profileStore = profileStore;
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
        // Session-only in-memory store: unifies LAN with the account-mode ProfileStore code
        // path so GameRoom always has a store to resolve loadouts from at game start, while
        // xpAwarder staying null (see grantVictoryArtifacts) keeps LAN from ever persisting
        // real acquisition/fusion.
        this.profileStore = new LanProfileStore();
        this.lanJoinCode = lanJoinCode;
        this.roomIdDigits = roomIdDigits;
        this.kryoServer = NetEndpoints.createServer();
        this.dispatcher = buildDispatcher();
    }

    ProfileStore getProfileStore() {
        return profileStore;
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

        // ---- Character/artifact profile packets (both modes) ----
        d.on(LoadoutRequest.class, w -> handleLoadoutRequest(w, sessionFor(w)));
        d.on(FusionRequest.class, w -> handleFusionRequest(w, sessionFor(w)));

        // ---- In-room packets (both modes) ----
        Consumer<ServerPacketWrapper> forward = w -> forwardToRoom(w, sessionFor(w));
        d.on(TextMessageRequest.class, forward);
        d.on(StartGameRequest.class, forward);
        d.on(MoveListRequest.class, forward);
        d.on(LocalPlayerCountRequest.class, forward);
        d.on(AbilityRequest.class, forward);
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

        if (req.playerName == null || req.playerName.trim().isEmpty()) {
            res.accepted = false;
            res.playerId = -1;
            res.reason = "missing username";
            sendTCP(w.connectionID, res);
            return;
        }

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
        // simply returns their existing slot.
        int localPlayers = req.localPlayers & 0xFF;
        GameRoom lanRoom = rooms.computeIfAbsent("LAN", id ->
                new GameRoom("LAN", this, w.connectionID, req.playerName, req.playerName,
                        localPlayers, null, null, profileStore));

        GameRoom.AddMemberResult add = lanRoom.tryAddMember(
                w.connectionID, req.playerName, session.accountUuid, localPlayers, GameConstants.MAX_PLAYERS);
        if (!add.success) {
            res.accepted = false;
            res.playerId = -1;
            res.reason = "could not join";
            sendTCP(w.connectionID, res);
            return;
        }

        res.accepted = true;
        res.playerId = add.firstActiveSlot;
        res.reason = "";
        res.gameInProgress = add.gameInProgress;
        res.spectatorOnly = add.spectatorOnly;
        sendTCP(w.connectionID, res);
        session.currentRoomId = "LAN";

        // Ensure room thread is running
        if (!lanRoom.isRunning()) {
            lanRoom.start();
        }

        // LAN profiles are session-only: every character unlocked, two pre-rolled artifacts,
        // no further acquisition or fusion (implementation.md, Part 5).
        session.profile = profileStore.loadProfile(session.accountUuid);
        session.profileReadOnly = true;
        sendProfileSync(w.connectionID, session);
    }

    // -------------------------------------------------------------------------
    // Character/artifact profile handlers (both modes)
    // -------------------------------------------------------------------------

    /** Loads (account mode) the profile for a freshly-authenticated session and syncs it to the client. */
    private void loadAndSyncProfile(int connectionId, Session session) {
        if (profileStore == null || session.accountUuid == null) return;
        session.profile = profileStore.loadProfile(session.accountUuid);
        session.profileReadOnly = false;
        sendProfileSync(connectionId, session);
    }

    private void sendProfileSync(int connectionId, Session session) {
        ProfileSyncBroadcast b = new ProfileSyncBroadcast();
        b.profile = session.profile;
        b.readOnly = session.profileReadOnly;
        sendTCP(connectionId, b);
    }

    private void handleLoadoutRequest(ServerPacketWrapper w, Session session) {
        if (session == null || session.profile == null) return;
        LoadoutRequest req = (LoadoutRequest) w.packet;
        PlayerProfile profile = session.profile;

        if (CharacterRegistry.byId(req.characterId) == null || !profile.isCharacterUnlocked(req.characterId)) {
            return; // silently ignore invalid/locked selection; client should not offer it
        }
        if (req.artifactIdA != null && profile.findArtifact(req.artifactIdA) == null) return;
        if (req.artifactIdB != null && profile.findArtifact(req.artifactIdB) == null) return;

        profile.selectedCharacterId = req.characterId;
        profile.equippedArtifactIds[0] = (req.artifactIdA != null && !req.artifactIdA.isEmpty()) ? req.artifactIdA : null;
        profile.equippedArtifactIds[1] = (req.artifactIdB != null && !req.artifactIdB.isEmpty()) ? req.artifactIdB : null;

        // Loadout selection is always allowed and saved (even in LAN, where saving just updates
        // the in-memory LanProfileStore so GameRoom sees it at game start); only acquisition and
        // fusion are blocked for read-only (LAN) profiles.
        if (profileStore != null) {
            profileStore.saveProfile(session.accountUuid, profile);
        }
        sendProfileSync(w.connectionID, session);
    }

    private void handleFusionRequest(ServerPacketWrapper w, Session session) {
        if (session == null || session.profile == null) return;
        FusionResultBroadcast res = new FusionResultBroadcast();

        if (session.profileReadOnly) {
            res.success = false;
            res.reason = "fusion is not available in LAN mode";
            sendTCP(w.connectionID, res);
            return;
        }

        FusionRequest req = (FusionRequest) w.packet;
        PlayerProfile profile = session.profile;
        if (req.artifactIds == null || req.artifactIds.length != 5) {
            res.success = false;
            res.reason = "fusion requires exactly 5 artifacts";
            sendTCP(w.connectionID, res);
            return;
        }

        java.util.List<Artifact> inputs = new java.util.ArrayList<>();
        for (String id : req.artifactIds) {
            Artifact a = profile.findArtifact(id);
            if (a == null) {
                res.success = false;
                res.reason = "artifact not owned: " + id;
                sendTCP(w.connectionID, res);
                return;
            }
            if (id.equals(profile.equippedArtifactIds[0]) || id.equals(profile.equippedArtifactIds[1])) {
                res.success = false;
                res.reason = "cannot fuse an equipped artifact";
                sendTCP(w.connectionID, res);
                return;
            }
            inputs.add(a);
        }

        try {
            me.ethanchen.game.progression.ArtifactFusion.Result fused =
                    me.ethanchen.game.progression.ArtifactFusion.fuse(inputs, rng);
            profile.inventory.removeIf(a -> {
                for (String id : req.artifactIds) if (id.equals(a.id)) return true;
                return false;
            });
            profile.inventory.add(fused.artifact);
            if (profileStore != null) {
                profileStore.saveProfile(session.accountUuid, profile);
            }
            res.success = true;
            res.reason = "";
            res.result = fused.artifact;
            sendTCP(w.connectionID, res);
            sendProfileSync(w.connectionID, session);
        } catch (IllegalArgumentException e) {
            res.success = false;
            res.reason = e.getMessage();
            sendTCP(w.connectionID, res);
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
        if (res.success) {
            loadAndSyncProfile(w.connectionID, session);
        }
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
        if (res.success) {
            loadAndSyncProfile(w.connectionID, session);
        }
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
        CreateRoomRequest createReq = (CreateRoomRequest) w.packet;
        int localPlayers = createReq.localPlayers & 0xFF;
        GameRoom room = new GameRoom(roomId, this, w.connectionID, session.username, session.accountUuid,
                localPlayers, resultRecorder, xpAwarder, profileStore);
        rooms.put(roomId, room);
        session.currentRoomId = roomId;
        room.start();

        RoomJoinResponse res = new RoomJoinResponse();
        res.success = true;
        res.reason = "";
        res.roomId = roomId;
        res.isHost = true;
        res.gameInProgress = false;
        res.spectatorOnly = false;
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
        int localPlayers = req.localPlayers & 0xFF;
        GameRoom.AddMemberResult add = room.tryAddMember(
                w.connectionID, session.username, session.accountUuid, localPlayers, GameConstants.MAX_PLAYERS);
        if (!add.success) {
            releaseAccountRoomSlot(session);
            RoomJoinResponse res = new RoomJoinResponse();
            res.success = false;
            res.reason = "could not join";
            sendTCP(w.connectionID, res);
            return;
        }
        session.currentRoomId = req.roomId;

        RoomJoinResponse res = new RoomJoinResponse();
        res.success = true;
        res.reason = "";
        res.roomId = req.roomId;
        res.isHost = false;
        res.gameInProgress = add.gameInProgress;
        res.spectatorOnly = add.spectatorOnly;
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
            info.spectatorCount = r.getSpectatorCount();
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
