package me.ethanchen.server;

import me.ethanchen.game.GameConstants;
import me.ethanchen.game.GameMode;
import me.ethanchen.game.board.Board;
import me.ethanchen.network.ServerPacketWrapper;
import me.ethanchen.network.packets.NetworkPacket;
import me.ethanchen.network.packets.c2s.MoveListRequest;
import me.ethanchen.network.packets.c2s.StartGameRequest;
import me.ethanchen.network.packets.c2s.TextMessageRequest;
import me.ethanchen.network.packets.s2c.RoomClosedBroadcast;
import me.ethanchen.network.packets.s2c.*;
import me.ethanchen.util.TextSanitizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GameRoom implements Runnable, GameRoomContext {

    public final String roomId;
    private final PacketSender sender;
    private final ConcurrentLinkedQueue<ServerPacketWrapper> inbound = new ConcurrentLinkedQueue<>();

    // Members: slot index (0..n-1) → connId; connId → slot
    private final List<Integer> slotToConn = new ArrayList<>();
    private final Map<Integer, Integer> connToSlot = new HashMap<>();
    private final Map<Integer, String> connToName = new HashMap<>();
    private final Map<Integer, String> connToUuid = new HashMap<>();
    private final int hostConnId;
    private final ResultRecorder resultRecorder;

    private volatile ServerGame serverGame;
    private volatile boolean running;
    private volatile boolean roomEmpty;
    private Thread thread;
    private int t;

    /** Used for LAN rooms, which never persist results. */
    public GameRoom(String roomId, PacketSender sender, int hostConnId, String hostName) {
        this(roomId, sender, hostConnId, hostName, hostName, null);
    }

    public GameRoom(String roomId, PacketSender sender, int hostConnId, String hostName, String hostUuid,
                     ResultRecorder resultRecorder) {
        this.roomId = roomId;
        this.sender = sender;
        this.hostConnId = hostConnId;
        this.resultRecorder = resultRecorder;
        addMemberUnconditional(hostConnId, hostName, hostUuid);
    }

    // -------------------------------------------------------------------------
    // Member management
    // -------------------------------------------------------------------------

    /**
     * Attempts to add {@code connId} to this room. Slot assignment, the in-progress check,
     * and the capacity check all happen atomically under this room's monitor, so concurrent
     * joins can never race each other into the same slot or overfill the room.
     *
     * @return the connection's slot index (a newly assigned slot, or the existing one if this
     *         connection is already a member), or {@code -1} if the join was rejected because
     *         a game is already in progress or the room is at {@code maxPlayers} capacity
     */
    public synchronized int tryAddMember(int connId, String name, String uuid, int maxPlayers) {
        Integer existing = connToSlot.get(connId);
        if (existing != null) return existing;
        if (serverGame != null && serverGame.isInProgress()) return -1;
        if (slotToConn.size() >= maxPlayers) return -1;
        return addMemberUnconditional(connId, name, uuid);
    }

    /**
     * Unconditionally adds a member and broadcasts the updated player list. Only safe to call
     * either before the room is published to other threads (the constructor), or from within a
     * method already synchronized on {@code this} (see {@link #tryAddMember}).
     */
    private int addMemberUnconditional(int connId, String name, String uuid) {
        int slot = slotToConn.size();
        slotToConn.add(connId);
        connToSlot.put(connId, slot);
        connToName.put(connId, name);
        connToUuid.put(connId, uuid);
        broadcastPlayerList();
        return slot;
    }

    /** Enqueue an inbound packet for processing on the room thread. */
    public void handlePacket(ServerPacketWrapper w) {
        inbound.add(w);
    }

    /**
     * Called when a client disconnects or sends LeaveRoomRequest.
     * Removes the member from this room. If the host leaves while no game is in progress,
     * broadcasts {@link RoomClosedBroadcast} to all remaining members and evicts them.
     *
     * @return list of connection IDs that were evicted as a side-effect (non-empty only when
     *         the host leaves in lobby state); callers must clear these sessions' currentRoomId.
     */
    public synchronized List<Integer> handleDisconnect(int connId) {
        List<Integer> evicted = new ArrayList<>();
        if (!connToSlot.containsKey(connId)) return evicted;

        // If the host leaves while no game is in progress, kick everyone else first.
        if (connId == hostConnId && (serverGame == null || !serverGame.isInProgress())) {
            RoomClosedBroadcast b = new RoomClosedBroadcast();
            b.reason = "host_left";
            for (int otherConnId : slotToConn) {
                if (otherConnId != connId) {
                    sender.sendTCP(otherConnId, b);
                    evicted.add(otherConnId);
                }
            }
            slotToConn.clear();
            connToSlot.clear();
            connToName.clear();
            connToUuid.clear();
            roomEmpty = true;
            return evicted;
        }

        // Normal removal: compact slot list.
        int slot = connToSlot.remove(connId);
        slotToConn.remove(slot);
        connToSlot.clear();
        for (int i = 0; i < slotToConn.size(); i++) {
            connToSlot.put(slotToConn.get(i), i);
        }
        connToName.remove(connId);
        connToUuid.remove(connId);

        if (serverGame != null && serverGame.isInProgress()) {
            serverGame.handleDisconnectedPlayer(slot);
        }

        broadcastPlayerList();

        if (slotToConn.isEmpty()) {
            roomEmpty = true;
        }
        return evicted;
    }

    public boolean isEmpty() {
        return roomEmpty || slotToConn.isEmpty();
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
                if (t % 10 == 0) {
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

    /**
     * Synchronized because it reads {@code connToName}/{@code connToSlot}, which are mutated
     * (under the same monitor) by {@link #tryAddMember} and {@link #handleDisconnect} on the
     * {@code ServerCore} thread while this runs on the room thread.
     */
    private synchronized void handleInboundPacket(ServerPacketWrapper w) {
        if (w.packet instanceof TextMessageRequest) {
            TextMessageRequest req = (TextMessageRequest) w.packet;
            String name = connToName.get(w.connectionID);
            if (name == null) return;
            TextMessageBroadcast b = new TextMessageBroadcast();
            b.sender = name;
            b.message = TextSanitizer.sanitizeChat(req.message);
            broadcastMembersTCP(b);
            return;
        }

        if (w.packet instanceof StartGameRequest) {
            if (w.connectionID != hostConnId) return; // only host can start
            if (serverGame != null && serverGame.isInProgress()) return;
            StartGameRequest req = (StartGameRequest) w.packet;
            startGame(req.gamemode);
            return;
        }

        if (w.packet instanceof MoveListRequest) {
            MoveListRequest req = (MoveListRequest) w.packet;
            Integer slot = connToSlot.get(w.connectionID);
            if (slot == null) return;
            if (serverGame != null && serverGame.isInProgress()) {
                serverGame.applyMoves(slot, req.ids, req.types);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Game start
    // -------------------------------------------------------------------------

    private synchronized void startGame(GameMode gameMode) {
        int playerCount = slotToConn.size();
        if (playerCount == 0) return;

        serverGame = new ServerGame(this);
        serverGame.startGame(gameMode, playerCount, 5000);

        long startTimeMs = System.currentTimeMillis() + 5000;

        // Build per-player name array (slot order)
        String[] playerNames = new String[playerCount];
        for (int i = 0; i < playerCount; i++) {
            Integer connId = slotToConn.get(i);
            playerNames[i] = connId != null ? connToName.getOrDefault(connId, "") : "";
        }

        // Send per-player StartGameBroadcast
        for (int i = 0; i < playerCount; i++) {
            Integer connId = slotToConn.get(i);
            if (connId == null) continue;

            StartGameBroadcast b = new StartGameBroadcast();
            b.mode = gameMode;
            b.boards = new Board.NetBoardFull[serverGame.getGame().getBoards().size()];
            for (int a = 0; a < b.boards.length; a++) {
                b.boards[a] = serverGame.getGame().getBoards().get(a).convertToNetBoardFull();
            }
            b.totalPlayers = (byte) playerCount;
            b.playerID = (byte) i;
            b.startTimeMS = startTimeMs;
            b.playerNames = playerNames;
            sender.sendTCP(connId, b);
        }

        System.out.println("[GameRoom " + roomId + "] Game started: mode=" + gameMode
                + " players=" + playerCount);
    }

    // -------------------------------------------------------------------------
    // GameRoomContext implementation (called by ServerGame)
    // -------------------------------------------------------------------------

    /**
     * Synchronized because it reads {@code slotToConn}/{@code connToName}, which are mutated
     * (under the same monitor) by {@link #tryAddMember} and {@link #handleDisconnect} on the
     * {@code ServerCore} thread while this runs on the room thread.
     */
    @Override
    public synchronized void sendNetUpdates() {
        if (serverGame == null || serverGame.getGame() == null) return;

        // Collect particles and spawners
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

        // Sound broadcasts via TCP
        ArrayList<PlacementSoundBroadcast> placementSounds = serverGame.getAndClearPendingPlacementSounds();
        if (placementSounds != null) {
            for (PlacementSoundBroadcast psb : placementSounds) {
                broadcastMembersTCP(psb);
            }
        }
        ArrayList<HoldSoundBroadcast> holdSounds = serverGame.getAndClearPendingHoldSounds();
        if (holdSounds != null) {
            for (HoldSoundBroadcast hsb : holdSounds) {
                broadcastMembersTCP(hsb);
            }
        }
        ArrayList<BumpSoundBroadcast> bumpSounds = serverGame.getAndClearPendingBumpSounds();
        if (bumpSounds != null) {
            for (BumpSoundBroadcast bsb : bumpSounds) {
                broadcastMembersUDP(bsb);
            }
        }

        // Pre-build board snapshots
        Board.NetBoardLight[] boardSnapshots =
                new Board.NetBoardLight[serverGame.getGame().getBoards().size()];
        for (int a = 0; a < boardSnapshots.length; a++) {
            boardSnapshots[a] = serverGame.getGame().getBoards().get(a).convertToNetBoardLight();
        }

        int playerCount = slotToConn.size();
        for (int i = 0; i < playerCount; i++) {
            Integer connId = slotToConn.get(i);
            if (connId == null) continue;

            LightGameStateBroadcast b = new LightGameStateBroadcast();
            b.boards = boardSnapshots;
            b.ackMoveId = serverGame.getHighestMoveId(i);
            b.piecesPlaced = serverGame.getPiecesPlaced();
            b.holdAvailable = serverGame.computeHoldAvailable(i);
            b.explodeProgress = serverGame.getExplodeProgress();
            b.ownPieceHoldGlow = serverGame.computeOwnPieceHoldGlow(i);
            b.gravity = serverGame.getGame().getGravity();
            b.gravityTickCounter = serverGame.getGame().getGravityTickCounter();
            b.gameEnded = serverGame.isGameEnded();
            switch (serverGame.getGame().getMode()) {
                case MULTIPLAYER_SCORE:
                    b.scoreMode = serverGame.getScoreModeData();
                    break;
                case MULTIPLAYER_PUZZLE:
                    b.puzzleMode = serverGame.getPuzzleModeData();
                    break;
                default:
                    break;
            }
            sender.sendUDP(connId, b);
            if (pb != null) {
                sender.sendUDP(connId, pb);
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
        int playerCount = slotToConn.size();
        b.playerNames = new String[playerCount];
        for (int i = 0; i < playerCount; i++) {
            Integer connId = slotToConn.get(i);
            b.playerNames[i] = connId != null ? connToName.getOrDefault(connId, "") : "";
        }
        broadcastMembersTCP(b);

        if (resultRecorder != null && info.mode != null && info.mode != GameMode.NONE) {
            GameResultData data = new GameResultData();
            data.gamemode = info.mode.name();
            data.win = info.win;
            data.disconnected = info.disconnected;
            data.score = info.score;
            data.displayScore = info.displayScore;
            data.extraJson = info.extraJson;
            data.timestampMs = System.currentTimeMillis();
            data.players = new PlayerResultInfo[playerCount];
            for (int i = 0; i < playerCount; i++) {
                Integer connId = slotToConn.get(i);
                String name = connId != null ? connToName.getOrDefault(connId, "") : "";
                String uuid = connId != null ? connToUuid.getOrDefault(connId, "") : "";
                data.players[i] = new PlayerResultInfo(name, uuid);
            }
            resultRecorder.recordGameResult(data);
        }
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
        b.playerNames = new String[slotToConn.size()];
        for (int i = 0; i < slotToConn.size(); i++) {
            Integer connId = slotToConn.get(i);
            b.playerNames[i] = connId != null ? connToName.getOrDefault(connId, "") : "";
        }
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
        return Collections.unmodifiableList(new ArrayList<>(slotToConn));
    }

    // -------------------------------------------------------------------------
    // Metadata accessors (used by ServerCore for RoomListBroadcast)
    // -------------------------------------------------------------------------

    public synchronized String getHostName() {
        return connToName.getOrDefault(hostConnId, "");
    }

    public synchronized int getPlayerCount() {
        return slotToConn.size();
    }

    public boolean isInProgress() {
        return serverGame != null && serverGame.isInProgress();
    }
}
