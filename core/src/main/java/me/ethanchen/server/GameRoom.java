package me.ethanchen.server;

import me.ethanchen.game.GameMode;
import me.ethanchen.game.board.Board;
import me.ethanchen.network.ServerPacketWrapper;
import me.ethanchen.network.packets.NetworkPacket;
import me.ethanchen.network.packets.c2s.MoveListRequest;
import me.ethanchen.network.packets.c2s.StartGameRequest;
import me.ethanchen.network.packets.c2s.TextMessageRequest;
import me.ethanchen.network.packets.s2c.RoomClosedBroadcast;
import me.ethanchen.network.packets.s2c.*;
import me.ethanchen.network.packets.s2c.gamemode.ScoreModeEndData;
import me.ethanchen.util.TextSanitizer;

import java.util.ArrayList;
import java.util.Collection;
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
    private final int hostConnId;

    private ServerGame serverGame;
    private volatile boolean running;
    private volatile boolean roomEmpty;
    private Thread thread;
    private int t;

    public GameRoom(String roomId, PacketSender sender, int hostConnId, String hostName) {
        this.roomId = roomId;
        this.sender = sender;
        this.hostConnId = hostConnId;
        addMember(hostConnId, hostName);
    }

    // -------------------------------------------------------------------------
    // Member management
    // -------------------------------------------------------------------------

    /**
     * Adds a member to this room and broadcasts the updated player list to all members.
     * Must only be called before a game starts.
     */
    public synchronized void addMember(int connId, String name) {
        if (connToSlot.containsKey(connId)) return;
        int slot = slotToConn.size();
        slotToConn.add(connId);
        connToSlot.put(connId, slot);
        connToName.put(connId, name);
        broadcastPlayerList();
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
            long sleep = 16 - elapsed;
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

    private void handleInboundPacket(ServerPacketWrapper w) {
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

    private synchronized void startGame(GameMode gamemode) {
        int playerCount = slotToConn.size();
        if (playerCount == 0) return;

        serverGame = new ServerGame(this);
        serverGame.startGame(gamemode, playerCount, 5000);

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
            b.mode = gamemode;
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

        System.out.println("[GameRoom " + roomId + "] Game started: mode=" + gamemode
                + " players=" + playerCount);
    }

    // -------------------------------------------------------------------------
    // GameRoomContext implementation (called by ServerGame)
    // -------------------------------------------------------------------------

    @Override
    public void sendNetUpdates() {
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
            switch (serverGame.getGame().getMode()) {
                case MULTIPLAYER_SCORE:
                    b.scoreMode = serverGame.getScoreModeData();
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
    public void sendEndGame(boolean win, ScoreModeEndData scoreEnd, boolean disconnected) {
        EndGameBroadcast b = new EndGameBroadcast();
        b.win = win;
        b.disconnected = disconnected;
        b.scoreModeEnd = scoreEnd;
        if (serverGame != null && serverGame.getGame() != null) {
            b.mode = serverGame.getGame().getMode();
        } else {
            b.mode = GameMode.NONE;
        }
        int playerCount = slotToConn.size();
        b.playerNames = new String[playerCount];
        for (int i = 0; i < playerCount; i++) {
            Integer connId = slotToConn.get(i);
            b.playerNames[i] = connId != null ? connToName.getOrDefault(connId, "") : "";
        }
        broadcastMembersTCP(b);
    }

    // -------------------------------------------------------------------------
    // Player list broadcasts
    // -------------------------------------------------------------------------

    private void broadcastPlayerList() {
        LobbyPlayerListBroadcast b = buildPlayerListBroadcast();
        broadcastMembersTCP(b);
    }

    private void broadcastPlayerListUDP() {
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

    private List<Integer> getMemberConnIds() {
        return Collections.unmodifiableList(new ArrayList<>(slotToConn));
    }

    // -------------------------------------------------------------------------
    // Metadata accessors (used by ServerCore for RoomListBroadcast)
    // -------------------------------------------------------------------------

    public String getHostName() {
        return connToName.getOrDefault(hostConnId, "");
    }

    public synchronized int getPlayerCount() {
        return slotToConn.size();
    }

    public boolean isInProgress() {
        return serverGame != null && serverGame.isInProgress();
    }
}
