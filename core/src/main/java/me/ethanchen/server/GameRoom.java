package me.ethanchen.server;

import me.ethanchen.game.GameConstants;
import me.ethanchen.game.GameMode;
import me.ethanchen.game.progression.Artifact;
import me.ethanchen.game.progression.ArtifactAcquisition;
import me.ethanchen.game.progression.CharacterDef;
import me.ethanchen.game.progression.CharacterRegistry;
import me.ethanchen.game.progression.PlayerProfile;
import me.ethanchen.network.PacketDispatcher;
import me.ethanchen.network.ServerPacketWrapper;
import me.ethanchen.network.dto.HardDropEffect;
import me.ethanchen.network.dto.NetBoardFull;
import me.ethanchen.network.dto.NetBoardLight;
import me.ethanchen.network.packets.NetworkPacket;
import me.ethanchen.network.packets.c2s.AbilityRequest;
import me.ethanchen.network.packets.c2s.LocalPlayerCountRequest;
import me.ethanchen.network.packets.c2s.MoveListRequest;
import me.ethanchen.network.packets.c2s.StartGameRequest;
import me.ethanchen.network.packets.c2s.TextMessageRequest;
import me.ethanchen.network.packets.s2c.*;
import me.ethanchen.util.TextSanitizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GameRoom implements Runnable, GameRoomContext {

    public final String roomId;
    private final PacketSender sender;
    private final ConcurrentLinkedQueue<ServerPacketWrapper> inbound = new ConcurrentLinkedQueue<>();

    /** Result of {@link #tryAddMember}. */
    public static final class AddMemberResult {
        public final boolean success;
        public final boolean spectatorOnly;
        public final boolean gameInProgress;
        public final int firstActiveSlot; // -1 if none / rejected

        public AddMemberResult(boolean success, boolean spectatorOnly, boolean gameInProgress, int firstActiveSlot) {
            this.success = success;
            this.spectatorOnly = spectatorOnly;
            this.gameInProgress = gameInProgress;
            this.firstActiveSlot = firstActiveSlot;
        }
    }

    private static final class Seat {
        int slot = -1; // -1 = spectating
        String displayName;
        String accountUuid; // empty for extra local players

        Seat(String displayName, String accountUuid) {
            this.displayName = displayName;
            this.accountUuid = accountUuid != null ? accountUuid : "";
        }
    }

    private static final class RoomMember {
        final int connId;
        String baseName;
        String accountUuid;
        final List<Seat> seats = new ArrayList<>();

        RoomMember(int connId, String baseName, String accountUuid, int localPlayers) {
            this.connId = connId;
            this.baseName = baseName;
            this.accountUuid = accountUuid != null ? accountUuid : "";
            rebuildSeats(localPlayers);
        }

        void rebuildSeats(int localPlayers) {
            seats.clear();
            int n = Math.max(0, Math.min(localPlayers, GameConstants.MAX_PLAYERS));
            for (int i = 0; i < n; i++) {
                String name = (i == 0) ? baseName : (baseName + " - " + (i + 1));
                String uuid = (i == 0) ? accountUuid : "";
                seats.add(new Seat(name, uuid));
            }
        }

        boolean hasActiveSeat() {
            for (Seat s : seats) {
                if (s.slot >= 0) return true;
            }
            return false;
        }

        int firstActiveSlot() {
            for (Seat s : seats) {
                if (s.slot >= 0) return s.slot;
            }
            return -1;
        }
    }

    private final List<RoomMember> members = new ArrayList<>();
    // Derived lookups rebuilt by reseat()
    private final List<Integer> slotToConn = new ArrayList<>();
    private final List<Integer> slotToLocalIndex = new ArrayList<>();
    private final Map<Integer, int[]> connToSlots = new HashMap<>();
    private final Map<Integer, RoomMember> connToMember = new HashMap<>();

    private int hostConnId;
    private final ResultRecorder resultRecorder;
    private final XpAwarder xpAwarder;
    private final ProfileStore profileStore;

    private volatile ServerGame serverGame;
    private volatile boolean running;
    private volatile boolean roomEmpty;
    private Thread thread;
    private int t;
    private final PacketDispatcher<ServerPacketWrapper> dispatcher = buildDispatcher();

    /** Used for LAN rooms, which never persist results. */
    public GameRoom(String roomId, PacketSender sender, int hostConnId, String hostName) {
        this(roomId, sender, hostConnId, hostName, hostName, 1, null, null, null);
    }

    public GameRoom(String roomId, PacketSender sender, int hostConnId, String hostName, String hostUuid,
                    int hostLocalPlayers, ResultRecorder resultRecorder, XpAwarder xpAwarder) {
        this(roomId, sender, hostConnId, hostName, hostUuid, hostLocalPlayers, resultRecorder, xpAwarder, null);
    }

    public GameRoom(String roomId, PacketSender sender, int hostConnId, String hostName, String hostUuid,
                    int hostLocalPlayers, ResultRecorder resultRecorder, XpAwarder xpAwarder, ProfileStore profileStore) {
        this.roomId = roomId;
        this.sender = sender;
        this.hostConnId = hostConnId;
        this.resultRecorder = resultRecorder;
        this.xpAwarder = xpAwarder;
        this.profileStore = profileStore;
        addMemberUnconditional(hostConnId, hostName, hostUuid, hostLocalPlayers);
    }

    /** Convenience for account-mode create where localPlayers defaults to 1. */
    public GameRoom(String roomId, PacketSender sender, int hostConnId, String hostName, String hostUuid,
                    ResultRecorder resultRecorder, XpAwarder xpAwarder, ProfileStore profileStore) {
        this(roomId, sender, hostConnId, hostName, hostUuid, 1, resultRecorder, xpAwarder, profileStore);
    }

    private PacketDispatcher<ServerPacketWrapper> buildDispatcher() {
        return new PacketDispatcher<ServerPacketWrapper>()
                .on(TextMessageRequest.class, this::handleTextMessage)
                .on(StartGameRequest.class, this::handleStartGameRequest)
                .on(MoveListRequest.class, this::handleMoveListRequest)
                .on(LocalPlayerCountRequest.class, this::handleLocalPlayerCountRequest)
                .on(AbilityRequest.class, this::handleAbilityRequest);
    }

    // -------------------------------------------------------------------------
    // Member management
    // -------------------------------------------------------------------------

    /**
     * Attempts to add {@code connId} to this room. Always succeeds unless the connection is
     * already a member (in which case the existing seating is returned). Overflow local players
     * become spectators. Mid-game joins are allowed as spectators.
     */
    public synchronized AddMemberResult tryAddMember(int connId, String name, String uuid,
                                                     int requestedLocalPlayers, int maxPlayers) {
        RoomMember existing = connToMember.get(connId);
        if (existing != null) {
            boolean inProgress = serverGame != null && serverGame.isInProgress();
            return new AddMemberResult(true, !existing.hasActiveSeat(), inProgress, existing.firstActiveSlot());
        }
        boolean inProgress = serverGame != null && serverGame.isInProgress();
        addMemberUnconditional(connId, name, uuid, requestedLocalPlayers);
        RoomMember m = connToMember.get(connId);
        if (inProgress) {
            sendSpectatorStartGame(connId);
        }
        return new AddMemberResult(true, !m.hasActiveSeat(), inProgress, m.firstActiveSlot());
    }

    private int addMemberUnconditional(int connId, String name, String uuid, int localPlayers) {
        RoomMember m = new RoomMember(connId, name, uuid, localPlayers);
        members.add(m);
        connToMember.put(connId, m);
        reseat();
        return m.firstActiveSlot();
    }

    public synchronized void setLocalPlayerCount(int connId, int count) {
        if (serverGame != null && serverGame.isInProgress()) return; // frozen during game
        RoomMember m = connToMember.get(connId);
        if (m == null) return;
        m.rebuildSeats(count);
        reseat();
    }

    /**
     * Assigns active slots 0..MAX_PLAYERS-1 in join order; remaining seats become spectators.
     * No-op while a game is in progress (frozen roster).
     */
    private void reseat() {
        if (serverGame != null && serverGame.isInProgress()) {
            rebuildLookups();
            broadcastPlayerList();
            return;
        }
        int nextSlot = 0;
        for (RoomMember m : members) {
            for (Seat s : m.seats) {
                if (nextSlot < GameConstants.MAX_PLAYERS) {
                    s.slot = nextSlot++;
                } else {
                    s.slot = -1;
                }
            }
        }
        rebuildLookups();
        broadcastPlayerList();
    }

    private void rebuildLookups() {
        slotToConn.clear();
        slotToLocalIndex.clear();
        connToSlots.clear();
        // Determine max active slot + 1
        int maxSlot = -1;
        for (RoomMember m : members) {
            for (Seat s : m.seats) {
                if (s.slot > maxSlot) maxSlot = s.slot;
            }
        }
        for (int i = 0; i <= maxSlot; i++) {
            slotToConn.add(null);
            slotToLocalIndex.add(null);
        }
        for (RoomMember m : members) {
            int[] slots = new int[m.seats.size()];
            for (int i = 0; i < m.seats.size(); i++) {
                Seat s = m.seats.get(i);
                slots[i] = s.slot;
                if (s.slot >= 0) {
                    while (slotToConn.size() <= s.slot) {
                        slotToConn.add(null);
                        slotToLocalIndex.add(null);
                    }
                    slotToConn.set(s.slot, m.connId);
                    slotToLocalIndex.set(s.slot, i);
                }
            }
            connToSlots.put(m.connId, slots);
        }
    }

    /** Enqueue an inbound packet for processing on the room thread. */
    public void handlePacket(ServerPacketWrapper w) {
        inbound.add(w);
    }

    /**
     * Called when a client disconnects or sends LeaveRoomRequest.
     * Removes the member from this room. If the departing member was the host and others
     * remain, host duties transfer to the earliest-joined remaining member. The room is
     * marked empty (and torn down by {@link ServerCore}) only when no members remain.
     *
     * @return list of connection IDs that were evicted as a side-effect (currently always
     *         empty; retained for API compatibility with {@link ServerCore#evictFromRoom})
     */
    public synchronized List<Integer> handleDisconnect(int connId) {
        List<Integer> evicted = new ArrayList<>();
        if (!connToMember.containsKey(connId)) return evicted;

        boolean wasHost = (connId == hostConnId);

        RoomMember removed = connToMember.remove(connId);
        members.remove(removed);
        boolean hadActive = removed != null && removed.hasActiveSeat();
        int firstSlot = removed != null ? removed.firstActiveSlot() : -1;

        if (serverGame != null && serverGame.isInProgress()) {
            if (hadActive) {
                serverGame.handleDisconnectedPlayer(firstSlot);
            }
            rebuildLookups();
            broadcastPlayerList();
        } else {
            reseat(); // promotes waiting spectators
        }

        if (members.isEmpty()) {
            roomEmpty = true;
            hostConnId = -1;
        } else if (wasHost) {
            // Earliest-joined remaining member becomes host (members is join-ordered).
            hostConnId = members.get(0).connId;
            broadcastHostChanged();
        }

        return evicted;
    }

    private void broadcastHostChanged() {
        RoomMember host = connToMember.get(hostConnId);
        String hostName = host != null ? host.baseName : "";
        for (RoomMember m : members) {
            HostChangedBroadcast b = new HostChangedBroadcast();
            b.youAreHost = (m.connId == hostConnId);
            b.hostName = hostName;
            sender.sendTCP(m.connId, b);
        }
    }

    public boolean isEmpty() {
        return roomEmpty || members.isEmpty();
    }

    // -------------------------------------------------------------------------
    // Thread lifecycle
    // -------------------------------------------------------------------------

    public void start() {
        running = true;
        thread = new Thread(this, "room-" + roomId);
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    public boolean isRunning() {
        return running;
    }

    // -------------------------------------------------------------------------
    // Room thread main loop
    // -------------------------------------------------------------------------

    @Override
    public void run() {
        while (running) {
            long start = System.currentTimeMillis();
            try {
                drainInbound();
                if (serverGame != null && serverGame.isInProgress()) {
                    serverGame.update();
                }
                if (t % GameConstants.LOBBY_UDP_REFRESH_INTERVAL_TICKS == 0) {
                    broadcastPlayerListUDP();
                }
            } catch (Exception e) {
                System.err.println("[GameRoom " + roomId + "] Uncaught exception: " + e);
                e.printStackTrace(System.err);
            }
            t++;
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
            handleInboundPacket(w);
        }
    }

    private synchronized void handleInboundPacket(ServerPacketWrapper w) {
        dispatcher.dispatch(w);
    }

    private void handleTextMessage(ServerPacketWrapper w) {
        TextMessageRequest req = (TextMessageRequest) w.packet;
        RoomMember m = connToMember.get(w.connectionID);
        if (m == null) return;
        TextMessageBroadcast b = new TextMessageBroadcast();
        b.sender = m.baseName;
        b.message = TextSanitizer.sanitizeChat(req.message);
        broadcastMembersTCP(b);
    }

    private void handleStartGameRequest(ServerPacketWrapper w) {
        if (w.connectionID != hostConnId) return; // only host can start
        if (serverGame != null && serverGame.isInProgress()) return;
        StartGameRequest req = (StartGameRequest) w.packet;
        startGame(req.gamemode);
    }

    private void handleMoveListRequest(ServerPacketWrapper w) {
        MoveListRequest req = (MoveListRequest) w.packet;
        int[] slots = connToSlots.get(w.connectionID);
        if (slots == null) return;
        int localIndex = req.localIndex & 0xFF;
        if (localIndex < 0 || localIndex >= slots.length) return;
        int slot = slots[localIndex];
        if (slot < 0) return;
        if (serverGame != null && serverGame.isInProgress()) {
            serverGame.applyMoves(slot, req.ids, req.types);
        }
    }

    private void handleLocalPlayerCountRequest(ServerPacketWrapper w) {
        LocalPlayerCountRequest req = (LocalPlayerCountRequest) w.packet;
        setLocalPlayerCount(w.connectionID, req.count & 0xFF);
    }

    // -------------------------------------------------------------------------
    // Game start / spectate sync
    // -------------------------------------------------------------------------

    private synchronized void startGame(GameMode gameMode) {
        // Promote any pending reseat before freezing
        if (serverGame == null || !serverGame.isInProgress()) {
            // Force a full reseat in case we were frozen somehow
            int nextSlot = 0;
            for (RoomMember m : members) {
                for (Seat s : m.seats) {
                    if (nextSlot < GameConstants.MAX_PLAYERS) {
                        s.slot = nextSlot++;
                    } else {
                        s.slot = -1;
                    }
                }
            }
            rebuildLookups();
        }

        int playerCount = getActiveSeatCount();
        if (playerCount == 0) return;

        serverGame = new ServerGame(this);
        ActiveLoadout[] loadouts = buildLoadouts(playerCount, gameMode.supportsCharacters());
        serverGame.startGame(gameMode, playerCount, GameConstants.GAME_START_DELAY_MS, loadouts);

        long startTimeMs = System.currentTimeMillis() + GameConstants.GAME_START_DELAY_MS;
        String[] playerNames = buildActivePlayerNames();

        for (RoomMember m : members) {
            StartGameBroadcast b = buildStartGameBroadcast(gameMode, playerCount, playerNames, startTimeMs, m, false);
            sender.sendTCP(m.connId, b);
        }

        System.out.println("[GameRoom " + roomId + "] Game started: mode=" + gameMode
                + " players=" + playerCount);
    }

    /**
     * Resolves each seated slot's equipped character and artifacts into a per-slot snapshot for
     * {@link ServerGame#startGame}, so a loadout change mid-game never affects the current game
     * (implementation.md, Part 4). Returns an all-null array (or {@code null} entries) for modes
     * that don't support characters, or slots without a resolvable profile.
     */
    private ActiveLoadout[] buildLoadouts(int playerCount, boolean charactersEnabled) {
        ActiveLoadout[] loadouts = new ActiveLoadout[playerCount];
        if (!charactersEnabled || profileStore == null) return loadouts;
        for (RoomMember m : members) {
            for (Seat s : m.seats) {
                if (s.slot < 0 || s.slot >= playerCount) continue;
                if (s.accountUuid == null || s.accountUuid.isEmpty()) continue;
                PlayerProfile profile = profileStore.loadProfile(s.accountUuid);
                CharacterDef character = CharacterRegistry.byId(profile.selectedCharacterId);
                if (character == null) character = CharacterRegistry.byId(0);
                Artifact a = profile.findArtifact(profile.equippedArtifactIds[0]);
                Artifact b = profile.findArtifact(profile.equippedArtifactIds[1]);
                loadouts[s.slot] = new ActiveLoadout(character, a, b);
            }
        }
        return loadouts;
    }

    private void handleAbilityRequest(ServerPacketWrapper w) {
        if (serverGame == null || !serverGame.isInProgress()) return;
        int[] slots = connToSlots.get(w.connectionID);
        if (slots == null) return;
        AbilityRequest req = (AbilityRequest) w.packet;
        int localIndex = req.localIndex & 0xFF;
        if (localIndex < 0 || localIndex >= slots.length) return;
        int slot = slots[localIndex];
        if (slot < 0) return;
        serverGame.activateAbility(slot);
    }

    private void sendSpectatorStartGame(int connId) {
        if (serverGame == null || !serverGame.isInProgress() || serverGame.getGame() == null) return;
        RoomMember m = connToMember.get(connId);
        if (m == null) return;
        String[] playerNames = buildActivePlayerNames();
        StartGameBroadcast b = buildStartGameBroadcast(
                serverGame.getGameMode(),
                getActiveSeatCount(),
                playerNames,
                serverGame.getGameStartMs(),
                m,
                true);
        sender.sendTCP(connId, b);
    }

    private StartGameBroadcast buildStartGameBroadcast(GameMode mode, int playerCount,
                                                       String[] playerNames, long startTimeMs,
                                                       RoomMember m, boolean spectatorJoin) {
        StartGameBroadcast b = new StartGameBroadcast();
        b.mode = mode;
        b.boards = new NetBoardFull[serverGame.getGame().getBoards().size()];
        for (int a = 0; a < b.boards.length; a++) {
            b.boards[a] = serverGame.getGame().getBoards().get(a).convertToNetBoardFull();
        }
        b.totalPlayers = (byte) playerCount;
        b.msUntilStart = startTimeMs - System.currentTimeMillis();
        b.playerNames = playerNames;
        b.spectatorJoin = spectatorJoin;
        // Active slots for this member, in local-player order
        List<Byte> ids = new ArrayList<>();
        for (Seat s : m.seats) {
            if (s.slot >= 0) ids.add((byte) s.slot);
        }
        b.localPlayerIds = new byte[ids.size()];
        for (int i = 0; i < ids.size(); i++) b.localPlayerIds[i] = ids.get(i);
        return b;
    }

    private String[] buildActivePlayerNames() {
        int playerCount = getActiveSeatCount();
        String[] playerNames = new String[playerCount];
        for (int i = 0; i < playerCount; i++) {
            playerNames[i] = "";
        }
        for (RoomMember m : members) {
            for (Seat s : m.seats) {
                if (s.slot >= 0 && s.slot < playerNames.length) {
                    playerNames[s.slot] = s.displayName;
                }
            }
        }
        return playerNames;
    }

    private int getActiveSeatCount() {
        int n = 0;
        for (RoomMember m : members) {
            for (Seat s : m.seats) {
                if (s.slot >= 0) n++;
            }
        }
        return n;
    }

    // -------------------------------------------------------------------------
    // GameRoomContext implementation (called by ServerGame)
    // -------------------------------------------------------------------------

    @Override
    public synchronized void sendNetUpdates() {
        if (serverGame == null || serverGame.getGame() == null) return;

        ArrayList<NetParticle> particles = serverGame.getAndClearPendingParticles();
        ArrayList<ParticleSpawner> spawners = serverGame.getAndClearPendingSpawners();
        ParticleBroadcast pb = null;
        boolean hasParticles = particles != null && !particles.isEmpty();
        boolean hasSpawners = spawners != null && !spawners.isEmpty();
        if (hasParticles || hasSpawners) {
            pb = new ParticleBroadcast();
            if (hasParticles) pb.particles = particles.toArray(new NetParticle[0]);
            if (hasSpawners) pb.spawners = spawners.toArray(new ParticleSpawner[0]);
        }

        ArrayList<HardDropEffect> hardDropEffects = serverGame.getAndClearPendingHardDropEffects();
        HardDropEffectsBroadcast hdeb = null;
        if (hardDropEffects != null && !hardDropEffects.isEmpty()) {
            hdeb = new HardDropEffectsBroadcast();
            hdeb.effects = hardDropEffects.toArray(new HardDropEffect[0]);
        }
        ArrayList<HoldSoundBroadcast> holdSounds = serverGame.getAndClearPendingHoldSounds();
        if (holdSounds != null) {
            for (HoldSoundBroadcast hsb : holdSounds) {
                broadcastMembersTCP(hsb);
            }
        }
        ArrayList<PieceSwapBroadcast> pieceSwaps = serverGame.getAndClearPendingPieceSwaps();
        if (pieceSwaps != null) {
            for (PieceSwapBroadcast psb : pieceSwaps) {
                broadcastMembersTCP(psb);
            }
        }
        ArrayList<BumpSoundBroadcast> bumpSounds = serverGame.getAndClearPendingBumpSounds();
        if (bumpSounds != null) {
            for (BumpSoundBroadcast bsb : bumpSounds) {
                broadcastMembersUDP(bsb);
            }
        }

        NetBoardLight[] boardSnapshots =
                new NetBoardLight[serverGame.getGame().getBoards().size()];
        for (int a = 0; a < boardSnapshots.length; a++) {
            boardSnapshots[a] = serverGame.getGame().getBoards().get(a).convertToNetBoardLight();
        }

        for (RoomMember m : members) {
            // Count active seats for this member
            int activeCount = 0;
            for (Seat s : m.seats) {
                if (s.slot >= 0) activeCount++;
            }
            LightGameStateBroadcast b = new LightGameStateBroadcast();
            b.boards = boardSnapshots;
            b.ackMoveIds = new int[activeCount];
            b.holdAvailable = new boolean[activeCount];
            b.ownPieceHoldGlow = new boolean[activeCount];
            int ai = 0;
            for (Seat s : m.seats) {
                if (s.slot < 0) continue;
                b.ackMoveIds[ai] = serverGame.getHighestMoveId(s.slot);
                b.holdAvailable[ai] = serverGame.computeHoldAvailable(s.slot);
                b.ownPieceHoldGlow[ai] = serverGame.computeOwnPieceHoldGlow(s.slot);
                ai++;
            }
            b.piecesPlaced = serverGame.getPiecesPlaced();
            b.explodeProgress = serverGame.getExplodeProgress();
            b.gravity = serverGame.getGame().getGravity();
            b.gravityTickCounter = serverGame.getGame().getGravityTickCounter();
            b.gameEnded = serverGame.isGameEnded();
            serverGame.populateModeData(b);
            sender.sendUDP(m.connId, b);
            if (pb != null) {
                sender.sendUDP(m.connId, pb);
            }
            if (hdeb != null) {
                sender.sendUDP(m.connId, hdeb);
            }
        }
    }

    @Override
    public synchronized void sendEndGame(GameEndInfo info) {
        EndGameBroadcast b = new EndGameBroadcast();
        b.win = info.win;
        b.disconnected = info.disconnected;
        b.scoreModeEnd = info.scoreModeEnd;
        b.puzzleModeEnd = info.puzzleModeEnd;
        b.mode = info.mode != null ? info.mode : GameMode.NONE;
        b.playerNames = buildActivePlayerNames();
        broadcastMembersTCP(b);

        if (info.mode != null && info.mode != GameMode.NONE) {
            // Build PlayerResultInfo from active seats (extras have empty UUID)
            List<PlayerResultInfo> playerList = new ArrayList<>();
            int playerCount = getActiveSeatCount();
            PlayerResultInfo[] bySlot = new PlayerResultInfo[playerCount];
            for (RoomMember m : members) {
                for (Seat s : m.seats) {
                    if (s.slot >= 0 && s.slot < bySlot.length) {
                        bySlot[s.slot] = new PlayerResultInfo(s.displayName, s.accountUuid);
                    }
                }
            }
            for (int i = 0; i < bySlot.length; i++) {
                playerList.add(bySlot[i] != null ? bySlot[i] : new PlayerResultInfo("", ""));
            }
            PlayerResultInfo[] players = playerList.toArray(new PlayerResultInfo[0]);
            if (resultRecorder != null) {
                resultRecorder.recordGameResult(GameResultData.from(info, players));
            }
            long xp = XpCalculator.computeXp(info.mode, info.score);
            if (xpAwarder != null && xp > 0) {
                for (PlayerResultInfo player : players) {
                    if (player.accountUuid != null && !player.accountUuid.isEmpty()) {
                        xpAwarder.awardXp(player.accountUuid, xp);
                    }
                }
            }

            // Artifact acquisition: only on victories in modes that grant xp, and only for real
            // (non-LAN) accounts -- LAN never persists xp (xpAwarder == null there) and must not
            // grant artifacts either (implementation.md, Part 5).
            if (info.win && xp > 0 && profileStore != null && xpAwarder != null) {
                grantVictoryArtifacts(xp);
            }
        }

        // Game ended — allow reseating for next round (triggered via onGameStopped after stopGame)
    }

    private final Random artifactRng = new Random();

    /**
     * Rolls and grants one artifact to each real (non-extra) seated player on a victory,
     * per implementation.md, Part 2. Extra local players (empty accountUuid) never earn xp and
     * so never receive artifacts either.
     */
    private void grantVictoryArtifacts(long xp) {
        for (RoomMember m : members) {
            for (Seat s : m.seats) {
                if (s.accountUuid == null || s.accountUuid.isEmpty()) continue;
                PlayerProfile profile = profileStore.loadProfile(s.accountUuid);
                Artifact artifact = ArtifactAcquisition.rollFromVictory(xp, artifactRng);
                profile.inventory.add(artifact);
                profile.sortInventory();
                profileStore.saveProfile(s.accountUuid, profile);

                ArtifactGrantBroadcast grant = new ArtifactGrantBroadcast();
                grant.artifact = artifact;
                sender.sendTCP(m.connId, grant);
            }
        }
    }

    @Override
    public synchronized void onGameStopped() {
        reseat();
    }

    // -------------------------------------------------------------------------
    // Player list broadcasts
    // -------------------------------------------------------------------------

    private synchronized void broadcastPlayerList() {
        LobbyPlayerListBroadcast b = buildPlayerListBroadcast();
        broadcastMembersTCP(b);
    }

    private synchronized void broadcastPlayerListUDP() {
        LobbyPlayerListBroadcast b = buildPlayerListBroadcast();
        broadcastMembersUDP(b);
    }

    private LobbyPlayerListBroadcast buildPlayerListBroadcast() {
        LobbyPlayerListBroadcast b = new LobbyPlayerListBroadcast();
        b.playerNames = buildActivePlayerNames();
        List<String> specs = new ArrayList<>();
        for (RoomMember m : members) {
            for (Seat s : m.seats) {
                if (s.slot < 0) specs.add(s.displayName);
            }
        }
        b.spectatorNames = specs.toArray(new String[0]);
        return b;
    }

    // -------------------------------------------------------------------------
    // Broadcast helpers
    // -------------------------------------------------------------------------

    private void broadcastMembersTCP(NetworkPacket packet) {
        for (int connId : getMemberConnIds()) {
            sender.sendTCP(connId, packet);
        }
    }

    private void broadcastMembersUDP(NetworkPacket packet) {
        for (int connId : getMemberConnIds()) {
            sender.sendUDP(connId, packet);
        }
    }

    private synchronized List<Integer> getMemberConnIds() {
        List<Integer> ids = new ArrayList<>(members.size());
        for (RoomMember m : members) ids.add(m.connId);
        return Collections.unmodifiableList(ids);
    }

    // -------------------------------------------------------------------------
    // Metadata accessors (used by ServerCore for RoomListBroadcast / tests)
    // -------------------------------------------------------------------------

    public synchronized String getHostName() {
        RoomMember host = connToMember.get(hostConnId);
        return host != null ? host.baseName : "";
    }

    public synchronized int getPlayerCount() {
        return getActiveSeatCount();
    }

    public synchronized int getSpectatorCount() {
        int n = 0;
        for (RoomMember m : members) {
            for (Seat s : m.seats) {
                if (s.slot < 0) n++;
            }
        }
        return n;
    }

    /** Package-visible for tests: current host connection id, or -1 if empty. */
    synchronized int getHostConnId() {
        return hostConnId;
    }

    /** Package-visible for tests: slot assigned to (connId, localIndex), or -1. */
    synchronized int getSlotFor(int connId, int localIndex) {
        int[] slots = connToSlots.get(connId);
        if (slots == null || localIndex < 0 || localIndex >= slots.length) return -1;
        return slots[localIndex];
    }

    public boolean isInProgress() {
        return serverGame != null && serverGame.isInProgress();
    }
}
