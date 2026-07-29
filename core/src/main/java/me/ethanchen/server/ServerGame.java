package me.ethanchen.server;

import java.util.ArrayList;
import java.util.Arrays;

import me.ethanchen.game.GameConstants;
import me.ethanchen.game.GameHandler;
import me.ethanchen.game.GameMode;
import me.ethanchen.game.board.Board;
import me.ethanchen.game.board.LineClearResult;
import me.ethanchen.game.board.MoveType;
import me.ethanchen.game.board.Piece;
import me.ethanchen.network.packets.s2c.BumpSoundBroadcast;
import me.ethanchen.network.packets.s2c.HoldSoundBroadcast;
import me.ethanchen.network.packets.s2c.NetParticle;
import me.ethanchen.network.packets.s2c.ParticleSpawner;
import me.ethanchen.network.packets.s2c.PlacementSoundBroadcast;
import me.ethanchen.network.packets.s2c.gamemode.PuzzleModeData;
import me.ethanchen.network.packets.s2c.gamemode.ScoreModeData;

/**
 * Authoritative server-side game instance. Orchestrates the per-tick update loop and delegates
 * each concern to a dedicated collaborator:
 *
 * <ul>
 *   <li>{@link PlacementEffects}       — particle/sound queuing
 *   <li>{@link ScoreModeScorer}        — MULTIPLAYER_SCORE state and scoring
 *   <li>{@link BlockedSpawnController} — blocked-piece cycling and hold-while-blocked
 *   <li>{@link GameEndController}      — win detection, grace period, and end-game broadcast
 * </ul>
 */
public class ServerGame {
    private volatile boolean inProgress; public boolean isInProgress() { return inProgress; }
    private volatile long lastUpdateMs;
    private int deltaTime;
    private GameMode gameMode;
    private int players;
    private GameHandler game;
    private GameRoomContext room;
    private int t;
    private int[] highestMoveId;
    private int[] piecesPlaced;
    private long[] hardDropBlockedUntilMs;
    private int[] bumpCounts;
    private int[] blockedCounts;

    private final PlacementEffects effects = new PlacementEffects();
    private final ScoreModeScorer scorer = new ScoreModeScorer();
    private final BlockedSpawnController blocked = new BlockedSpawnController();
    private final GameEndController endCtrl = new GameEndController();

    public ServerGame(GameRoomContext room) {
        inProgress = false;
        gameMode = GameMode.NONE;
        this.room = room;
    }

    /**
     * Starts a new game. Synchronized (along with the other mutation entry points below) so
     * that a disconnect arriving on the {@code ServerCore} thread via
     * {@link #handleDisconnectedPlayer(int)} can never interleave with an in-flight
     * {@link #update()}/{@link #applyMoves(int, int[], byte[])} call on the room thread.
     */
    public synchronized boolean startGame(GameMode gameMode, int players, int msToStart) {
        if (inProgress) return false;
        inProgress = true;
        lastUpdateMs = System.currentTimeMillis();
        this.gameMode = gameMode;
        this.players = players;
        this.game = new GameHandler(players);
        this.game.init(gameMode, msToStart);

        long gameStartMs    = System.currentTimeMillis() + msToStart;
        long gameEndTargetMs = gameStartMs + GameConstants.SCORE_MODE_DURATION_MS;

        this.highestMoveId = new int[players];
        this.piecesPlaced = new int[players];
        this.hardDropBlockedUntilMs = new long[players];
        this.bumpCounts = new int[players];
        this.blockedCounts = new int[players];
        Arrays.fill(this.highestMoveId, -1);

        scorer.reset(players, game);
        blocked.reset(players);
        endCtrl.reset(gameStartMs, gameEndTargetMs);
        t = 0;
        return true;
    }

    public synchronized void stopGame() {
        this.gameMode = GameMode.NONE;
        this.game = null;
        this.players = 0;
        this.highestMoveId = null;
        inProgress = false;
        room.onGameStopped();
    }

    public int getHighestMoveId(int playerId) {
        if (highestMoveId == null || playerId < 0 || playerId >= highestMoveId.length) return -1;
        return highestMoveId[playerId];
    }

    public synchronized void applyMoves(int playerId, int[] ids, byte[] types) {
        if (!inProgress || endCtrl.isGameEnded() || game == null || ids == null || types == null) return;
        if (ids.length != types.length) return;
        if (playerId < 0 || playerId >= players) return;
        if (game.getBoards().isEmpty()) return;
        Board board = game.getBoards().get(0);
        if (board.getActivePieces().size() <= playerId) return;
        MoveType[] moveValues = MoveType.values();
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] <= highestMoveId[playerId]) continue;
            highestMoveId[playerId] = ids[i];
            if (types[i] < 0 || types[i] >= moveValues.length) continue;
            MoveType move = moveValues[types[i]];
            if (move == MoveType.HARD_DROP) {
                if (System.currentTimeMillis() < hardDropBlockedUntilMs[playerId]) {
                    // suppressed after auto-lock
                } else {
                    LineClearResult result = board.hardDrop(playerId);
                    if (result != null && result.placed) {
                        processPlacement(result);
                    } else if (result != null && result.blockedByPlayerId >= 0) {
                        checkBlocked(board, playerId, result.blockedByPlayerId);
                    }
                }
            } else if (move == MoveType.HOLD) {
                if (!computeHoldAvailable(playerId)) {
                    effects.addHoldSound((byte) playerId, false);
                } else {
                    Piece currentPiece = board.getActivePieces().size() > playerId
                            ? board.getActivePieces().get(playerId) : null;
                    if (currentPiece != null && currentPiece.isBlockedFromSpawning) {
                        blocked.applyBlockedHold(playerId, board, effects);
                    } else if (board.useHold(playerId)) {
                        blocked.setLastHoldUsedMs(System.currentTimeMillis());
                        effects.addHoldSound((byte) playerId, true);
                    }
                }
            } else {
                boolean moved = board.applyMove(playerId, move);
                if (!moved && (move == MoveType.LEFT || move == MoveType.RIGHT)) {
                    Piece moverPiece = board.getActivePiece(playerId);
                    if (!moverPiece.isBlockedFromSpawning) {
                        int xdiff = (move == MoveType.LEFT) ? -1 : 1;
                        int blockerId = board.getLateralBlocker(playerId, xdiff);
                        if (blockerId >= 0) {
                            checkBump(board, playerId, blockerId);
                        }
                    }
                }
                LineClearResult lockResult = board.tryMovementLock(playerId);
                if (lockResult != null && lockResult.placed) {
                    processPlacement(lockResult);
                    if (!lockResult.manual) {
                        hardDropBlockedUntilMs[playerId] = System.currentTimeMillis() + GameConstants.HARD_DROP_SUPPRESS_MS;
                    }
                }
            }
        }
    }

    /**
     * Shared post-placement logic: increments the placement counter, applies mode-specific
     * scoring, and queues sounds/particles.
     */
    private void processPlacement(LineClearResult result) {
        piecesPlaced[result.playerId]++;
        int priorCombo = game.getCombo();
        if (gameMode == GameMode.MULTIPLAYER_SCORE) {
            scorer.scoreHardDrop(result, effects);
        } else {
            game.applyClearToCounters(result);
        }
        effects.queuePlacementSound(result, priorCombo);
        effects.queueResultParticles(result, game.getBoards().get(0).bw());
    }

    // -------------------------------------------------------------------------
    // Bump / blocked events
    // -------------------------------------------------------------------------

    /** Below this recent-movement threshold, a player counts as having moved "recently". */
    private static final float BUMP_TIMER_THRESHOLD_MS = 400f;

    private void checkBump(Board board, int playerA, int playerB) {
        if (board.getActivePiece(playerA).movementTimer < BUMP_TIMER_THRESHOLD_MS
                && board.getActivePiece(playerB).movementTimer < BUMP_TIMER_THRESHOLD_MS) {
            bumpedEvent(playerA, playerB);
        }
    }

    private void checkBlocked(Board board, int droppedPlayerId, int blockingPlayerId) {
        if (board.getActivePiece(blockingPlayerId).movementTimer < BUMP_TIMER_THRESHOLD_MS) {
            blockedEvent(droppedPlayerId, blockingPlayerId);
        }
    }

    /** Stub: fired when two players mutually block each other's lateral movement while
     *  both moved/rotated/soft-dropped recently. More functionality to come later. */
    private void bumpedEvent(int playerA, int playerB) {
        if (playerA >= 0 && playerA < bumpCounts.length) bumpCounts[playerA]++;
        if (playerB >= 0 && playerB < bumpCounts.length) bumpCounts[playerB]++;
        effects.addBumpSound((byte) playerA, (byte) playerB, false);
    }

    /** Stub: fired when a hard-dropped piece rests on another player's recently-moved
     *  piece without locking. More functionality to come later. */
    private void blockedEvent(int droppedPlayerId, int blockingPlayerId) {
        if (droppedPlayerId >= 0 && droppedPlayerId < blockedCounts.length) blockedCounts[droppedPlayerId]++;
        effects.addBumpSound((byte) droppedPlayerId, (byte) blockingPlayerId, true);
    }

    // -------------------------------------------------------------------------
    // Main update loop (unified for all modes)
    // -------------------------------------------------------------------------

    public synchronized void update() {
        deltaTime = (int)(System.currentTimeMillis() - lastUpdateMs);

        switch (gameMode) {
            case MULTIPLAYER_SCORE:
            case MULTIPLAYER_PUZZLE:
                updateGameTick();
                if (game != null) sendNetUpdates();
                break;
            default:
                break;
        }

        endCtrl.tickGrace(gameMode, scorer, room, this::stopGame);

        lastUpdateMs = System.currentTimeMillis();
        t++;
    }

    /**
     * Common per-tick update shared by all active game modes. The only mode-specific behaviour
     * (the win condition) is delegated to {@link GameEndController#checkWinCondition}.
     */
    private void updateGameTick() {
        if (endCtrl.isGameEnded()) return;
        game.update(deltaTime);
        for (LineClearResult r : game.getAndClearPendingLockResults()) {
            if (r.placed) {
                processPlacement(r);
                if (!r.manual) {
                    hardDropBlockedUntilMs[r.playerId] = System.currentTimeMillis() + GameConstants.HARD_DROP_SUPPRESS_MS;
                }
            }
        }
        if (game.isStarted() && !endCtrl.isGameEnded()) {
            blocked.update(deltaTime / 1000f, players, game,
                    () -> endCtrl.beginGameEndLoss(gameMode, scorer, bumpCounts, blockedCounts));
            endCtrl.checkWinCondition(gameMode, game, scorer, bumpCounts, blockedCounts);
        }
    }

    // -------------------------------------------------------------------------
    // Public accessors (delegating to collaborators)
    // -------------------------------------------------------------------------

    public ArrayList<NetParticle> getAndClearPendingParticles() {
        return effects.getAndClearPendingParticles();
    }

    public ArrayList<ParticleSpawner> getAndClearPendingSpawners() {
        return effects.getAndClearPendingSpawners();
    }

    public ArrayList<PlacementSoundBroadcast> getAndClearPendingPlacementSounds() {
        return effects.getAndClearPendingPlacementSounds();
    }

    public ArrayList<HoldSoundBroadcast> getAndClearPendingHoldSounds() {
        return effects.getAndClearPendingHoldSounds();
    }

    public ArrayList<BumpSoundBroadcast> getAndClearPendingBumpSounds() {
        return effects.getAndClearPendingBumpSounds();
    }

    public boolean computeHoldAvailable(int playerId) {
        if (game == null || game.getBoards().isEmpty()) return true;
        return blocked.computeHoldAvailable(playerId, game.getBoards().get(0));
    }

    public boolean computeOwnPieceHoldGlow(int playerId) {
        if (game == null || game.getBoards().isEmpty()) return false;
        return blocked.computeOwnPieceHoldGlow(playerId, game.getBoards().get(0));
    }

    public float getExplodeProgress() {
        return blocked.getExplodeProgress();
    }

    public ScoreModeData getScoreModeData() {
        return scorer.getScoreModeData();
    }

    public PuzzleModeData getPuzzleModeData() {
        PuzzleModeData d = new PuzzleModeData();
        d.elapsedMs = endCtrl.getFrozenPuzzleElapsedMs(endCtrl.getGameStartMs(), game != null && game.isStarted());
        return d;
    }

    public boolean isGameEnded() {
        return endCtrl.isGameEnded();
    }

    /**
     * Populates the mode-specific data fields of {@code b} for the current game mode.
     * Replaces the {@code switch (gameMode)} that previously lived in
     * {@link GameRoom#sendNetUpdates}.
     */
    public void populateModeData(me.ethanchen.network.packets.s2c.LightGameStateBroadcast b) {
        if (gameMode == me.ethanchen.game.GameMode.MULTIPLAYER_SCORE) {
            b.scoreMode = scorer.getScoreModeData();
        } else if (gameMode == me.ethanchen.game.GameMode.MULTIPLAYER_PUZZLE) {
            b.puzzleMode = getPuzzleModeData();
        }
    }

    public void sendNetUpdates() {
        if (t % GameConstants.NET_UPDATE_BROADCAST_INTERVAL_TICKS == 0 && game != null) {
            room.sendNetUpdates();
        }
    }

    public GameHandler getGame() {
        return game;
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public long getGameStartMs() {
        return endCtrl.getGameStartMs();
    }

    public int[] getPiecesPlaced() {
        return piecesPlaced;
    }

    /**
     * Called when a player disconnects mid-game.
     */
    public synchronized void handleDisconnectedPlayer(int id) {
        endCtrl.beginGameEndDisconnect(gameMode, scorer, bumpCounts, blockedCounts);
    }
}
