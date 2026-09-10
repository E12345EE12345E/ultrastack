package me.ethanchen.server;

import com.esotericsoftware.kryonet.Server;
import me.ethanchen.game.GameConstants;
import me.ethanchen.game.progression.Artifact;
import me.ethanchen.game.progression.CharacterRegistry;
import me.ethanchen.game.progression.GachaRoll;
import me.ethanchen.game.progression.GachaTables;
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
    private final RoomScheduler roomScheduler = new RoomScheduler();
    private final PersistenceExecutor persistence = new PersistenceExecutor();

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
            // Account-mode clients that land here used to hang forever: dispatch dropped
            // Login/Register with no reply. Always reject with an AuthResponse.
            d.on(LoginRequest.class, this::handleLanAuthReject);
            d.on(RegisterRequest.class, this::handleLanAuthReject);
        } else {
            // ---- Account mode: auth + room packets ----
            d.on(JoinRequest.class, this::handleAccountJoinReject);
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
        d.on(DealerRequest.class, w -> handleDealerRequest(w, sessionFor(w)));
        d.on(ProfileViewRequest.class, w -> handleProfileViewRequest(w, sessionFor(w)));

        // ---- In-room packets (both modes) ----
        Consumer<ServerPacketWrapper> forward = w -> forwardToRoom(w, sessionFor(w));
        d.on(TextMessageRequest.class, forward);
        d.on(StartGameRequest.class, forward);
        d.on(LobbySettingsRequest.class, forward);
        d.on(MoveListRequest.class, forward);
        d.on(LocalPlayerCountRequest.class, forward);
        d.on(AbilityRequest.class, forward);
        d.on(SpectateRequest.class, forward);
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
        roomScheduler.start();
        loopThread = new Thread(this, "server-core-loop");
        loopThread.setDaemon(true);
        loopThread.start();
        System.out.println("[ServerCore] Started on port " + port);
    }

    public void stop() {
        running = false;
        for (GameRoom room : rooms.values()) room.stop();
        roomScheduler.stop();
        persistence.shutdown();
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
            } catch (Throwable t) {
                Uncaught.log("[ServerCore] Uncaught exception in loop: ", t);
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
            try {
                dispatch(w);
            } catch (Throwable t) {
                String type = w.packet != null ? w.packet.getClass().getSimpleName() : "null";
                Uncaught.log("[ServerCore] Uncaught exception dispatching " + type
                        + " from connId=" + w.connectionID + ": ", t);
                replyAuthFailure(w.connectionID, w.packet, "server error");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Dispatch
    // -------------------------------------------------------------------------

    private void dispatch(ServerPacketWrapper w) {
        if (sessionFor(w) == null) return; // shouldn't happen, but guard
        if (!dispatcher.dispatch(w)) {
            String type = w.packet != null ? w.packet.getClass().getSimpleName() : "null";
            System.err.println("[ServerCore] Unhandled packet " + type
                    + " from connId=" + w.connectionID);
        }
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
        GameRoom lanRoom = rooms.computeIfAbsent("LAN", id -> {
            GameRoom created = new GameRoom("LAN", this, w.connectionID, req.playerName, req.playerName,
                    localPlayers, null, null, profileStore);
            created.attachRuntime(roomScheduler, persistence);
            return created;
        });

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
        if (session.profile != null) session.profile.sortInventory();
        ProfileSyncBroadcast b = new ProfileSyncBroadcast();
        b.profile = session.profile;
        b.readOnly = session.profileReadOnly;
        sendTCP(connectionId, b);
    }

    private void handleProfileViewRequest(ServerPacketWrapper w, Session session) {
        if (session == null || !session.authenticated) return;
        ProfileViewRequest req = (ProfileViewRequest) w.packet;
        String accountUuid = req.accountUuid;
        int connectionId = w.connectionID;
        if (accountUuid == null || accountUuid.isEmpty() || profileStore == null) {
            ProfileViewResponse res = new ProfileViewResponse();
            res.accountUuid = accountUuid;
            res.found = false;
            sendTCP(connectionId, res);
            return;
        }
        persistence.submit(() -> completeProfileView(connectionId, session, accountUuid));
    }

    private void completeProfileView(int connectionId, Session session, String accountUuid) {
        if (!sessionStillCurrent(connectionId, session)) return;
        ProfileViewResponse res = new ProfileViewResponse();
        res.accountUuid = accountUuid;
        try {
            profileStore.ensureBestsBackfilled(accountUuid, resultRecorder);
            PublicAccountView view = profileStore.loadPublicView(accountUuid);
            if (view == null) {
                res.found = false;
                sendTCP(connectionId, res);
                return;
            }
            res.found = true;
            res.username = view.username;
            res.xp = view.xp;
            res.selectedCharacterId = view.selectedCharacterId;
            res.equippedA = view.equippedA;
            res.equippedB = view.equippedB;
            res.bestScore = view.bestScore;
            res.bestPuzzle = view.bestPuzzle;
            res.bestCharacterScore = view.bestCharacterScore;
        } catch (Throwable t) {
            Uncaught.log("[ServerCore] Profile view failed for connId=" + connectionId + ": ", t);
            res.found = false;
        }
        if (!sessionStillCurrent(connectionId, session)) return;
        sendTCP(connectionId, res);
    }

    private void handleLoadoutRequest(ServerPacketWrapper w, Session session) {
        if (session == null || session.profile == null) return;
        LoadoutRequest req = (LoadoutRequest) w.packet;
        int connectionId = w.connectionID;
        int characterId = req.characterId;
        String artifactIdA = req.artifactIdA;
        String artifactIdB = req.artifactIdB;
        persistence.submit(() -> completeLoadout(connectionId, session, characterId, artifactIdA, artifactIdB));
    }

    private void completeLoadout(int connectionId, Session session, int characterId,
                                 String artifactIdA, String artifactIdB) {
        if (!sessionStillCurrent(connectionId, session) || session.profile == null) return;
        PlayerProfile profile = session.profile;

        if (CharacterRegistry.byId(characterId) == null || !profile.isCharacterUnlocked(characterId)) {
            return; // silently ignore invalid/locked selection; client should not offer it
        }
        if (artifactIdA != null && profile.findArtifact(artifactIdA) == null) return;
        if (artifactIdB != null && profile.findArtifact(artifactIdB) == null) return;

        profile.selectedCharacterId = characterId;
        profile.equippedArtifactIds[0] = (artifactIdA != null && !artifactIdA.isEmpty()) ? artifactIdA : null;
        profile.equippedArtifactIds[1] = (artifactIdB != null && !artifactIdB.isEmpty()) ? artifactIdB : null;

        // Loadout selection is always allowed and saved (even in LAN, where saving just updates
        // the in-memory LanProfileStore so GameRoom sees it at game start); only acquisition and
        // fusion are blocked for read-only (LAN) profiles.
        try {
            if (profileStore != null) {
                profileStore.saveProfile(session.accountUuid, profile);
            }
        } catch (Throwable t) {
            Uncaught.log("[ServerCore] Loadout save failed for connId=" + connectionId + ": ", t);
        }
        if (!sessionStillCurrent(connectionId, session)) return;
        sendProfileSync(connectionId, session);
        if (session.currentRoomId != null) {
            GameRoom room = rooms.get(session.currentRoomId);
            if (room != null) room.refreshPlayerList();
        }
    }

    private void handleFusionRequest(ServerPacketWrapper w, Session session) {
        if (session == null || session.profile == null) return;
        FusionRequest req = (FusionRequest) w.packet;
        String[] artifactIds = req.artifactIds == null ? null : req.artifactIds.clone();
        persistence.submit(() -> completeFusion(w.connectionID, session, artifactIds));
    }

    private void completeFusion(int connectionId, Session session, String[] artifactIds) {
        if (!sessionStillCurrent(connectionId, session) || session.profile == null) return;
        FusionResultBroadcast res = new FusionResultBroadcast();

        if (session.profileReadOnly) {
            res.success = false;
            res.reason = "fusion is not available in LAN mode";
            sendTCP(connectionId, res);
            return;
        }

        PlayerProfile profile = session.profile;
        if (artifactIds == null || artifactIds.length != 5) {
            res.success = false;
            res.reason = "fusion requires exactly 5 artifacts";
            sendTCP(connectionId, res);
            return;
        }

        java.util.List<Artifact> inputs = new java.util.ArrayList<>();
        for (String id : artifactIds) {
            Artifact a = profile.findArtifact(id);
            if (a == null) {
                res.success = false;
                res.reason = "artifact not owned: " + id;
                sendTCP(connectionId, res);
                return;
            }
            if (id.equals(profile.equippedArtifactIds[0]) || id.equals(profile.equippedArtifactIds[1])) {
                res.success = false;
                res.reason = "cannot fuse an equipped artifact";
                sendTCP(connectionId, res);
                return;
            }
            inputs.add(a);
        }

        try {
            me.ethanchen.game.progression.ArtifactFusion.Result fused =
                    me.ethanchen.game.progression.ArtifactFusion.fuse(inputs, new Random());
            profile.inventory.removeIf(a -> {
                for (String id : artifactIds) if (id.equals(a.id)) return true;
                return false;
            });
            profile.inventory.add(fused.artifact);
            profile.sortInventory();
            if (profileStore != null) {
                profileStore.saveProfile(session.accountUuid, profile);
            }
            res.success = true;
            res.reason = "";
            res.result = fused.artifact;
            if (!sessionStillCurrent(connectionId, session)) return;
            sendTCP(connectionId, res);
            sendProfileSync(connectionId, session);
        } catch (IllegalArgumentException e) {
            res.success = false;
            res.reason = e.getMessage();
            if (sessionStillCurrent(connectionId, session)) sendTCP(connectionId, res);
        } catch (Throwable t) {
            Uncaught.log("[ServerCore] Fusion failed for connId=" + connectionId + ": ", t);
            res.success = false;
            res.reason = "fusion failed";
            if (sessionStillCurrent(connectionId, session)) sendTCP(connectionId, res);
        }
    }

    private void handleDealerRequest(ServerPacketWrapper w, Session session) {
        if (session == null || session.profile == null) return;
        int count = ((DealerRequest) w.packet).count;
        persistence.submit(() -> completeDeal(w.connectionID, session, count));
    }

    private void completeDeal(int connectionId, Session session, int count) {
        if (!sessionStillCurrent(connectionId, session) || session.profile == null) return;
        DealerResultBroadcast res = new DealerResultBroadcast();

        if (session.profileReadOnly) {
            res.success = false;
            res.reason = "the card dealer is not available in LAN mode";
            sendTCP(connectionId, res);
            return;
        }

        final long cost;
        try {
            cost = GachaTables.costFor(count);
        } catch (IllegalArgumentException e) {
            res.success = false;
            res.reason = e.getMessage();
            sendTCP(connectionId, res);
            return;
        }

        PlayerProfile profile = session.profile;
        if (profile.tokenBalance() < cost) {
            res.success = false;
            res.reason = "not enough tokens";
            sendTCP(connectionId, res);
            return;
        }

        try {
            List<GachaRoll.Card> cards = GachaRoll.deal(count, new Random());
            if (!profile.spendTokens(cost)) {
                res.success = false;
                res.reason = "not enough tokens";
                sendTCP(connectionId, res);
                return;
            }

            res.rarities = new byte[cards.size()];
            res.artifacts = new Artifact[cards.size()];
            for (int i = 0; i < cards.size(); i++) {
                GachaRoll.Card card = cards.get(i);
                res.rarities[i] = card.rarity;
                res.artifacts[i] = card.artifact;
                profile.inventory.add(card.artifact);
            }
            profile.sortInventory();
            if (profileStore != null) profileStore.saveProfile(session.accountUuid, profile);

            res.success = true;
            res.reason = "";
            res.tokensSpent = cost;
            if (!sessionStillCurrent(connectionId, session)) return;
            sendTCP(connectionId, res);
            sendProfileSync(connectionId, session);
        } catch (Throwable t) {
            Uncaught.log("[ServerCore] Card Dealer failed for connId=" + connectionId + ": ", t);
            res.success = false;
            res.reason = "card dealer failed";
            if (sessionStillCurrent(connectionId, session)) sendTCP(connectionId, res);
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

    private void handleLanAuthReject(ServerPacketWrapper w) {
        String type = w.packet != null ? w.packet.getClass().getSimpleName() : "packet";
        System.out.println("[ServerCore] Rejected " + type + " on LAN server from connId="
                + w.connectionID);
        sendAuthResponse(w.connectionID, false, "this is a LAN server", null);
    }

    private void handleAccountJoinReject(ServerPacketWrapper w) {
        System.out.println("[ServerCore] Rejected JoinRequest on account server from connId="
                + w.connectionID);
        JoinResponse res = new JoinResponse();
        res.accepted = false;
        res.playerId = -1;
        res.reason = "this is not a LAN server";
        sendTCP(w.connectionID, res);
    }

    private void handleLogin(ServerPacketWrapper w, Session session) {
        if (session == null) {
            sendAuthResponse(w.connectionID, false, "server error", null);
            return;
        }
        LoginRequest req = (LoginRequest) w.packet;
        String versionError = protocolVersionMismatchReason(req.protocolVersion);
        if (versionError != null) {
            sendAuthResponse(w.connectionID, false, versionError, null);
            return;
        }
        // Copy fields: KryoNet may reuse the packet object before the persistence task runs.
        int connectionId = w.connectionID;
        String username = req.username;
        String passcode = req.passcode;
        persistence.submit(() -> completeLogin(connectionId, session, username, passcode));
    }

    private void handleRegister(ServerPacketWrapper w, Session session) {
        if (session == null) {
            sendAuthResponse(w.connectionID, false, "server error", null);
            return;
        }
        RegisterRequest req = (RegisterRequest) w.packet;
        String versionError = protocolVersionMismatchReason(req.protocolVersion);
        if (versionError != null) {
            sendAuthResponse(w.connectionID, false, versionError, null);
            return;
        }
        int connectionId = w.connectionID;
        String username = req.username;
        String passcode = req.passcode;
        persistence.submit(() -> completeRegister(connectionId, session, username, passcode));
    }

    private void completeLogin(int connectionId, Session session, String username, String passcode) {
        if (!sessionStillCurrent(connectionId, session)) return;
        AuthResponse res = new AuthResponse();
        try {
            String error = authProvider.login(username, passcode, session);
            if (error == null) {
                res.success = true;
                res.reason = "";
                res.accountUuid = session.accountUuid;
                session.username = username;
                session.authenticated = true;
            } else {
                res.success = false;
                res.reason = error;
            }
        } catch (Throwable t) {
            Uncaught.log("[ServerCore] Login failed for connId=" + connectionId + ": ", t);
            res.success = false;
            res.reason = "authentication failed";
        }
        finishAuth(connectionId, session, res);
    }

    private void completeRegister(int connectionId, Session session, String username, String passcode) {
        if (!sessionStillCurrent(connectionId, session)) return;
        AuthResponse res = new AuthResponse();
        try {
            String error = authProvider.register(username, passcode);
            if (error == null) {
                // Registration succeeded — also authenticate the session so the player
                // can immediately use room operations without a separate login step.
                String loginError = authProvider.login(username, passcode, session);
                if (loginError == null) {
                    res.success = true;
                    res.reason = "";
                    res.accountUuid = session.accountUuid;
                } else {
                    res.success = false;
                    res.reason = "registered but login failed: " + loginError;
                }
            } else {
                res.success = false;
                res.reason = error;
            }
        } catch (Throwable t) {
            Uncaught.log("[ServerCore] Register failed for connId=" + connectionId + ": ", t);
            res.success = false;
            res.reason = "registration failed";
        }
        finishAuth(connectionId, session, res);
    }

    private void finishAuth(int connectionId, Session session, AuthResponse res) {
        if (!sessionStillCurrent(connectionId, session)) return;
        sendTCP(connectionId, res);
        if (res.success) {
            loadAndSyncProfile(connectionId, session);
        }
    }

    /** True if {@code session} is still the live record for this KryoNet connection id. */
    private boolean sessionStillCurrent(int connectionId, Session session) {
        return session != null && sessions.get(connectionId) == session;
    }

    private void sendAuthResponse(int connectionId, boolean success, String reason, String accountUuid) {
        AuthResponse res = new AuthResponse();
        res.success = success;
        res.reason = reason != null ? reason : "";
        res.accountUuid = accountUuid;
        sendTCP(connectionId, res);
    }

    private void replyAuthFailure(int connectionId, NetworkPacket packet, String reason) {
        if (packet instanceof LoginRequest || packet instanceof RegisterRequest) {
            sendAuthResponse(connectionId, false, reason, null);
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
        room.attachRuntime(roomScheduler, persistence);
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
            info.gamemode = r.getPendingGamemode();
            info.playerNames = r.getPlayerNames();
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
