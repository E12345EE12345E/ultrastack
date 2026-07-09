package me.ethanchen.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import com.badlogic.gdx.utils.Json;

import me.ethanchen.game.GameConstants;
import me.ethanchen.game.GameHandler;
import me.ethanchen.game.GameMode;
import me.ethanchen.game.board.Board;
import me.ethanchen.game.board.LineClearResult;
import me.ethanchen.game.board.MoveType;
import me.ethanchen.game.board.Piece;
import me.ethanchen.game.board.SpinType;
import me.ethanchen.network.packets.s2c.BumpSoundBroadcast;
import me.ethanchen.network.packets.s2c.HoldSoundBroadcast;
import me.ethanchen.network.packets.s2c.NetParticle;
import me.ethanchen.network.packets.s2c.ParticleSpawner;
import me.ethanchen.network.packets.s2c.PlacementSoundBroadcast;
import me.ethanchen.network.packets.s2c.gamemode.PuzzleModeData;
import me.ethanchen.network.packets.s2c.gamemode.PuzzleModeEndData;
import me.ethanchen.network.packets.s2c.gamemode.ScoreModeData;
import me.ethanchen.network.packets.s2c.gamemode.ScoreModeEndData;

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
    private final ArrayList<NetParticle> pendingParticles = new ArrayList<>();
    private final ArrayList<ParticleSpawner> pendingSpawners = new ArrayList<>();
    private final ArrayList<PlacementSoundBroadcast> pendingPlacementSounds = new ArrayList<>();
    private final ArrayList<HoldSoundBroadcast> pendingHoldSounds = new ArrayList<>();
    private final ArrayList<BumpSoundBroadcast> pendingBumpSounds = new ArrayList<>();
    private int[] piecesPlaced;

    // Hold state
    private long lastHoldUsedMs = 0;

    // Bump/blocked event threshold
    private static final float BUMP_TIMER_THRESHOLD_MS = 400f;

    // Per-player hard-drop suppression after auto-lock
    private long[] hardDropBlockedUntilMs;

    // Per-player blocked-cycling state (re-initialized in startGame)
    private float[]  timeBetweenNextPiece;
    private float[]  cycleTimer;
    private long[]   lastCycleSwitchMs;
    private byte[]   previousCyclePieceId;
    private boolean[] wasBlocked;

    // Explode / end-game state
    private float   explodeCountdown = -1f;
    private boolean gameEnded        = false;

    // Game-end grace period: set once by beginGameEnd(), consumed by finalizeGameEnd() once
    // gameEndGraceUntilMs elapses (see GameConstants.PUZZLE_GAME_END_GRACE_MS).
    private boolean pendingWin;
    private boolean pendingDisconnected;
    private long gameEndGraceUntilMs;
    private ScoreModeEndData frozenScoreEnd;
    private PuzzleModeEndData frozenPuzzleEnd;
    private long frozenPuzzleElapsedMs;

    // Timer state
    private long gameStartMs;
    private long gameEndTargetMs;

    // MULTIPLAYER_SCORE mode state
    private long totalScore;
    private int glowPlayerId;
    private int repeatColumn;
    private int repeatColumn2;
    private float[] glowValues;
    private final Random scoreRng = new Random();

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
        gameStartMs     = System.currentTimeMillis() + msToStart;
        gameEndTargetMs = gameStartMs + GameConstants.SCORE_MODE_DURATION_MS;
        this.highestMoveId = new int[players];
        this.piecesPlaced = new int[players];
        this.hardDropBlockedUntilMs = new long[players];
        Arrays.fill(this.highestMoveId, -1);
        // Hold state reset
        lastHoldUsedMs = 0;
        // Blocked-cycling state reset
        timeBetweenNextPiece = new float[players];
        cycleTimer           = new float[players];
        lastCycleSwitchMs    = new long[players];
        previousCyclePieceId = new byte[players];
        wasBlocked           = new boolean[players];
        Arrays.fill(timeBetweenNextPiece, GameConstants.CYCLE_START);
        explodeCountdown = -1f;
        gameEnded        = false;
        pendingWin = false;
        pendingDisconnected = false;
        gameEndGraceUntilMs = 0;
        frozenScoreEnd = null;
        frozenPuzzleEnd = null;
        frozenPuzzleElapsedMs = 0;
        // Score-mode state reset
        totalScore = 0;
        glowPlayerId = -1;
        repeatColumn = -1;
        repeatColumn2 = -1;
        glowValues = new float[players];
        Arrays.fill(glowValues, 0.5f);
        t = 0;
        return true;
    }

    public synchronized void stopGame() {
        this.gameMode = GameMode.NONE;
        this.game = null;
        this.players = 0;
        this.highestMoveId = null;
        inProgress = false;
    }

    public int getHighestMoveId(int playerId) {
        if (highestMoveId == null || playerId < 0 || playerId >= highestMoveId.length) return -1;
        return highestMoveId[playerId];
    }

    public synchronized void applyMoves(int playerId, int[] ids, byte[] types) {
        if (!inProgress || gameEnded || game == null || ids == null || types == null) return;
        if (ids.length != types.length) return; // malformed/corrupt request
        if (playerId < 0 || playerId >= players) return;
        if (game.getBoards().isEmpty()) return;
        Board board = game.getBoards().get(0);
        if (board.getActivePieces().size() <= playerId) return;
        MoveType[] moveValues = MoveType.values();
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] <= highestMoveId[playerId]) continue;
            // Ack the move id immediately: it's the client's own monotonic counter, so once
            // seen it must never be replayed, even if the move below turns out to be a no-op.
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
                    }
                }
            } else if (move == MoveType.HOLD) {
                if (!computeHoldAvailable(playerId)) {
                    HoldSoundBroadcast hsb = new HoldSoundBroadcast();
                    hsb.playerId = (byte) playerId;
                    hsb.success = false;
                    pendingHoldSounds.add(hsb);
                } else {
                    Piece currentPiece = board.getActivePieces().size() > playerId
                            ? board.getActivePieces().get(playerId) : null;
                    if (currentPiece != null && currentPiece.isBlockedFromSpawning) {
                        applyBlockedHold(playerId, board);
                    } else if (board.useHold(playerId)) {
                        lastHoldUsedMs = System.currentTimeMillis();
                        HoldSoundBroadcast hsb = new HoldSoundBroadcast();
                        hsb.playerId = (byte) playerId;
                        hsb.success = true;
                        pendingHoldSounds.add(hsb);
                    }
                }
            } else {
                board.applyMove(playerId, move);
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
     * Shared post-placement logic: increments the placement counter, updates the universal
     * B2B/combo counters, applies mode-specific scoring, and queues sounds/particles. Used by
     * hard drops, movement-overflow locks, and timer locks.
     */
    private void processPlacement(LineClearResult result) {
        piecesPlaced[result.playerId]++;
        int priorCombo = game.getCombo();
        switch (gameMode) {
            case MULTIPLAYER_SCORE:
                // Scores the drop (glow/diff-column bonuses, totalScore, gravity ramp — all
                // MULTIPLAYER_SCORE-specific) and updates the B2B/combo counters internally.
                scoreHardDrop(result);
                break;
            default:
                // B2B and combo are universal mechanics (drive combo sounds, etc.) even in
                // modes with no score-specific bonuses to compute.
                game.applyClearToCounters(result);
                break;
        }
        queuePlacementSound(result, priorCombo);
        queueResultParticles(result);
    }

    /**
     * Returns the accumulated individual particle events and clears the internal list.
     */
    public ArrayList<NetParticle> getAndClearPendingParticles() {
        if (pendingParticles.isEmpty()) return null;
        ArrayList<NetParticle> copy = new ArrayList<>(pendingParticles);
        pendingParticles.clear();
        return copy;
    }

    /**
     * Returns the accumulated compact spawner events and clears the internal list.
     */
    public ArrayList<ParticleSpawner> getAndClearPendingSpawners() {
        if (pendingSpawners.isEmpty()) return null;
        ArrayList<ParticleSpawner> copy = new ArrayList<>(pendingSpawners);
        pendingSpawners.clear();
        return copy;
    }

    /** Returns accumulated placement-sound events and clears the list. */
    public ArrayList<PlacementSoundBroadcast> getAndClearPendingPlacementSounds() {
        if (pendingPlacementSounds.isEmpty()) return null;
        ArrayList<PlacementSoundBroadcast> copy = new ArrayList<>(pendingPlacementSounds);
        pendingPlacementSounds.clear();
        return copy;
    }

    /** Returns accumulated hold-sound events and clears the list. */
    public ArrayList<HoldSoundBroadcast> getAndClearPendingHoldSounds() {
        if (pendingHoldSounds.isEmpty()) return null;
        ArrayList<HoldSoundBroadcast> copy = new ArrayList<>(pendingHoldSounds);
        pendingHoldSounds.clear();
        return copy;
    }

    /** Returns accumulated bump-sound events and clears the list. */
    public ArrayList<BumpSoundBroadcast> getAndClearPendingBumpSounds() {
        if (pendingBumpSounds.isEmpty()) return null;
        ArrayList<BumpSoundBroadcast> copy = new ArrayList<>(pendingBumpSounds);
        pendingBumpSounds.clear();
        return copy;
    }

    /**
     * Builds and queues a {@link PlacementSoundBroadcast} for the given placement result.
     *
     * @param priorCombo the combo counter value captured <em>before</em> applyClearToCounters ran
     */
    private void queuePlacementSound(LineClearResult result, int priorCombo) {
        PlacementSoundBroadcast psb = new PlacementSoundBroadcast();
        psb.playerId = (byte) result.playerId;

        int lines = result.numClearedRows();
        switch (result.spinType) {
            case T_SPIN:
            case T_SPIN_MINI:
                psb.spinType = 2;
                break;
            case ALL_SPIN:
            case SMALL_SPIN:
                psb.spinType = 3;
                break;
            default:
                psb.spinType = (lines == 4) ? (byte) 1 : (byte) 0;
                break;
        }

        psb.combo = (lines > 0) ? (byte) priorCombo : (byte) -1;
        pendingPlacementSounds.add(psb);
    }

    // -------------------------------------------------------------------------
    // Bump / blocked events
    // -------------------------------------------------------------------------

    private void checkBump(int playerA, int playerB) {
        Board board = game.getBoards().get(0);
        if (board.getActivePiece(playerA).movementTimer < BUMP_TIMER_THRESHOLD_MS
                && board.getActivePiece(playerB).movementTimer < BUMP_TIMER_THRESHOLD_MS) {
            bumpedEvent(playerA, playerB);
        }
    }

    private void checkBlocked(int droppedPlayerId, int blockingPlayerId) {
        Board board = game.getBoards().get(0);
        if (board.getActivePiece(blockingPlayerId).movementTimer < BUMP_TIMER_THRESHOLD_MS) {
            blockedEvent(droppedPlayerId, blockingPlayerId);
        }
    }

    /** Stub: fired when two players mutually block each other's lateral movement while
     *  both moved/rotated/soft-dropped recently. More functionality to come later. */
    private void bumpedEvent(int playerA, int playerB) {
        BumpSoundBroadcast b = new BumpSoundBroadcast();
        b.playerId = (byte) playerA;
        b.otherPlayerId = (byte) playerB;
        b.blocked = false;
        pendingBumpSounds.add(b);
    }

    /** Stub: fired when a hard-dropped piece rests on another player's recently-moved
     *  piece without locking. More functionality to come later. */
    private void blockedEvent(int droppedPlayerId, int blockingPlayerId) {
        BumpSoundBroadcast b = new BumpSoundBroadcast();
        b.playerId = (byte) droppedPlayerId;
        b.otherPlayerId = (byte) blockingPlayerId;
        b.blocked = true;
        pendingBumpSounds.add(b);
    }

    // -------------------------------------------------------------------------
    // MULTIPLAYER_SCORE scoring
    // -------------------------------------------------------------------------

    private void scoreHardDrop(LineClearResult result) {
        int lines = result.numClearedRows();
        if (lines == 0) {
            game.applyClearToCounters(result);
            return;
        }

        // Read counters BEFORE updating them
        int priorB2b = game.getB2b();
        int priorCombo = game.getCombo();
        int priorComboPlayer = game.getPreviousComboPlayerId();
        boolean eligible = GameHandler.isB2BEligible(result);

        // --- Determine active bonuses ---
        boolean b2bBonus    = eligible && priorB2b >= 1;
        boolean comboBonus  = priorCombo >= 1 && priorComboPlayer != result.playerId;
        boolean glowBonus   = result.playerId == glowPlayerId;
        boolean diffColBonus = !clearedTilesHitRepeatColumn(result);

        // --- Base score ---
        long base = baseScore(result.spinType, lines);

        // --- Apply multipliers (stacking multiplicatively) ---
        double multiplier = 1.0;
        if (b2bBonus)    multiplier *= GameConstants.B2B_MULTIPLIER;
        if (comboBonus)  multiplier *= GameConstants.COMBO_MULTIPLIER;
        if (glowBonus)   multiplier *= GameConstants.GLOW_MULTIPLIER;
        if (diffColBonus) multiplier *= GameConstants.DIFF_COLUMN_MULTIPLIER;
        long points = Math.round(base * multiplier);
        totalScore += points;

        // --- Spawn popup particles at the piece center ---
        float cx = result.restingCenterX;
        float cy = result.restingCenterY;

        NetParticle scoreParticle = new NetParticle();
        scoreParticle.boardIndex = 0;
        scoreParticle.kind = 2; // POPUP_SCORE
        scoreParticle.tileType = result.pieceType;
        scoreParticle.x = cx;
        scoreParticle.y = cy;
        scoreParticle.value = (int) Math.min(points, Integer.MAX_VALUE);
        pendingParticles.add(scoreParticle);

        int bonusBits = (b2bBonus    ? 1 : 0)
                      | (diffColBonus ? 2 : 0)
                      | (comboBonus  ? 4 : 0)
                      | (glowBonus   ? 8 : 0);
        if (bonusBits != 0) {
            NetParticle multParticle = new NetParticle();
            multParticle.boardIndex = 0;
            multParticle.kind = 3; // POPUP_SCORE_MULTIPLIER
            multParticle.tileType = result.pieceType;
            multParticle.x = cx;
            multParticle.y = cy;
            multParticle.value = bonusBits;
            pendingParticles.add(multParticle);
        }

        // --- Update glow state ---
        if (glowBonus) glowPlayerId = -1;
        if (eligible && players > 1) {
            glowPlayerId = randomOtherPlayer(result.playerId);
        }
        rebuildGlowValues();

        // --- Update repeat columns ---
        updateRepeatColumns(result);

        // --- Update global counters ---
        game.applyClearToCounters(result);

        // --- Gravity ramp: each cleared line speeds up gravity ---
        int newGravity = game.getGravity();
        for (int i = 0; i < lines; i++) {
            newGravity = (int) Math.max(GameConstants.GRAVITY_FLOOR_MS, newGravity * GameConstants.GRAVITY_RAMP);
        }
        game.setGravity(newGravity);
    }

    private static long baseScore(SpinType spinType, int lines) {
        switch (spinType) {
            case T_SPIN:
                switch (lines) {
                    case 1: return 400;
                    case 2: return 800;
                    case 3: return 1200;
                }
                break;
            case T_SPIN_MINI:
                switch (lines) {
                    case 1: return 200;
                    case 2: return 800;
                }
                break;
            case ALL_SPIN:
                switch (lines) {
                    case 1: return 150;
                    case 2: return 300;
                    case 3: return 450;
                    case 4: return 800;
                }
                break;
            case SMALL_SPIN:
                switch (lines) {
                    case 1: return 200;
                    case 2: return 400;
                    case 3: return 600;
                }
                break;
            default:
                break;
        }
        switch (lines) {
            case 1: return 100;
            case 2: return 200;
            case 3: return 300;
            case 4: return 800;
        }
        return 0;
    }

    /**
     * Returns true if any tile placed in the cleared rows intersects with the current
     * repeatColumn or repeatColumn2 markers.
     */
    private boolean clearedTilesHitRepeatColumn(LineClearResult result) {
        if (repeatColumn == -1 && repeatColumn2 == -1) return false;
        for (int[] cols : result.filledColumnsPerClearedRow) {
            for (int col : cols) {
                if (col == repeatColumn || col == repeatColumn2) return true;
            }
        }
        return false;
    }

    private void updateRepeatColumns(LineClearResult result) {
        float cx = result.restingCenterX;
        byte type = result.pieceType;
        byte rot  = result.pieceRotation;
        if (type == Piece.I && (rot == 0 || rot == 2)) {
            repeatColumn  = (int) Math.floor(cx - 0.5f);
            repeatColumn2 = (int) Math.floor(cx + 0.5f);
        } else if (type == Piece.O) {
            repeatColumn  = (int) Math.floor(cx - 0.5f);
            repeatColumn2 = (int) Math.floor(cx + 0.5f);
        } else if (type == Piece.I && rot == 1) {
            repeatColumn  = (int) Math.floor(cx + 0.5f);
            repeatColumn2 = -1;
        } else if (type == Piece.I && rot == 3) {
            repeatColumn  = (int) Math.floor(cx - 0.5f);
            repeatColumn2 = -1;
        } else {
            repeatColumn  = (int) Math.floor(cx);
            repeatColumn2 = -1;
        }
    }

    private int randomOtherPlayer(int excludeId) {
        int count = players - 1;
        if (count <= 0) return -1;
        int pick = scoreRng.nextInt(count);
        if (pick >= excludeId) pick++;
        return pick;
    }

    private void rebuildGlowValues() {
        if (glowValues == null || glowValues.length != players) {
            glowValues = new float[players];
        }
        Arrays.fill(glowValues, 0.25f);
        if (glowPlayerId >= 0 && glowPlayerId < players) {
            glowValues[glowPlayerId] = 2f;
        }
    }

    public boolean computeHoldAvailable(int playerId) {
        if (game == null || game.getBoards().isEmpty()) return true;
        Board board = game.getBoards().get(0);
        if (board.getActivePieces().size() > playerId
                && board.getActivePieces().get(playerId).isBlockedFromSpawning) {
            if (explodeCountdown >= 0f) return false;
            return canHoldWhileBlocked(playerId);
        }
        long now = System.currentTimeMillis();
        boolean globalLock = lastHoldUsedMs > 0 && (now - lastHoldUsedMs) < GameConstants.HOLD_GLOBAL_LOCK_MS;
        return !board.isPlayerHoldUsed(playerId) && !globalLock;
    }

    public ScoreModeData getScoreModeData() {
        ScoreModeData d = new ScoreModeData();
        d.glowingValues = (glowValues != null) ? Arrays.copyOf(glowValues, glowValues.length) : new float[0];
        d.totalScore    = totalScore;
        d.repeatColumn  = repeatColumn;
        d.repeatColumn2 = repeatColumn2;
        return d;
    }

    // -------------------------------------------------------------------------

    /**
     * Translates a {@link LineClearResult} into compact {@link ParticleSpawner} events and
     * any remaining individual {@link NetParticle} events, then queues them for the next
     * broadcast cycle.
     */
    private void queueResultParticles(LineClearResult result) {
        // Hard-drop flash: one spawner encodes the whole piece
        if (!result.placedCells.isEmpty()) {
            ParticleSpawner ps = new ParticleSpawner();
            ps.spawnerType = ParticleSpawner.TYPE_HARD_DROP;
            ps.boardIndex = 0;
            ps.pieceType = result.pieceType;
            ps.doubledX = (byte) Math.floor(result.restingCenterX * 2);
            ps.doubledY = (byte) Math.floor(result.restingCenterY * 2);
            ps.pieceRotation = result.pieceRotation;
            pendingSpawners.add(ps);
        }

        // Line-clear tile-break: one spawner per cleared row
        if (result.clearedRows.length > 0) {
            Board board = game.getBoards().get(0);
            int boardWidth = board.bw();

            for (int row : result.clearedRows) {
                byte[] tileIds = new byte[boardWidth];
                Arrays.fill(tileIds, (byte) -1);
                for (int[] cell : result.clearedCells) {
                    if (cell[1] == row) {
                        tileIds[cell[0]] = (byte) cell[2];
                    }
                }
                ParticleSpawner ps = new ParticleSpawner();
                ps.spawnerType = ParticleSpawner.TYPE_LINE_CLEAR;
                ps.boardIndex = 0;
                ps.lineY = (byte) row;
                ps.tileIds = tileIds;
                pendingSpawners.add(ps);
            }
        }

        // Broken cells: kept as individual NetParticles
        for (int[] cell : result.brokenCells) {
            NetParticle np = new NetParticle();
            np.boardIndex = 0;
            np.kind = 1; // TILE_BREAK
            np.tileType = (byte) cell[2];
            np.x = cell[0];
            np.y = cell[1];
            pendingParticles.add(np);
        }
    }

    // -------------------------------------------------------------------------
    // Blocked-spawn cycling, hold-while-blocked, and end-game
    // -------------------------------------------------------------------------

    /**
     * Returns the effective piece-cycling interval for player {@code i}.
     */
    private float effectiveInterval(int i) {
        if (explodeCountdown >= 0f) {
            float frac = Math.min(explodeCountdown, 1f);
            return GameConstants.CYCLE_MIN + (GameConstants.EXPLODE_MIN_INTERVAL - GameConstants.CYCLE_MIN) * frac;
        }
        return timeBetweenNextPiece[i];
    }

    /**
     * Returns true when player {@code i}'s blocked piece may be held.
     */
    public boolean canHoldWhileBlocked(int i) {
        if (timeBetweenNextPiece == null || i < 0 || i >= players) return false;
        return timeBetweenNextPiece[i] <= GameConstants.CYCLE_MIN && explodeCountdown < 0f;
    }

    public boolean computeOwnPieceHoldGlow(int playerId) {
        if (game == null || game.getBoards().isEmpty()) return false;
        Board board = game.getBoards().get(0);
        if (board.getActivePieces().size() <= playerId) return false;
        Piece p = board.getActivePieces().get(playerId);
        return p.isBlockedFromSpawning && canHoldWhileBlocked(playerId);
    }

    public float getExplodeProgress() {
        return explodeCountdown;
    }

    /**
     * Core per-frame blocked-cycling update.
     */
    private void updateBlockedCycling(float dtSec) {
        if (game == null || game.getBoards().isEmpty()) return;
        Board board = game.getBoards().get(0);
        if (board.getActivePieces().isEmpty()) return;

        long now = System.currentTimeMillis();

        for (int i = 0; i < players; i++) {
            if (i >= board.getActivePieces().size()) continue;
            Piece piece = board.getActivePieces().get(i);
            boolean blocked = piece.isBlockedFromSpawning;

            if (blocked && !wasBlocked[i]) {
                timeBetweenNextPiece[i] = GameConstants.CYCLE_START;
                cycleTimer[i] = 0f;
            }
            if (!blocked && wasBlocked[i]) {
                cycleTimer[i] = 0f;
            }
            wasBlocked[i] = blocked;

            if (!blocked) continue;

            cycleTimer[i] += dtSec;
            float interval = effectiveInterval(i);
            while (cycleTimer[i] >= interval) {
                cycleTimer[i] -= interval;
                previousCyclePieceId[i] = board.getActivePieces().get(i).type;
                lastCycleSwitchMs[i] = now;
                board.spawnNextPiece(i);
                timeBetweenNextPiece[i] = Math.max(GameConstants.CYCLE_MIN, timeBetweenNextPiece[i] * GameConstants.CYCLE_MULT);
                interval = effectiveInterval(i);
                Piece newPiece = board.getActivePieces().get(i);
                if (!newPiece.isBlockedFromSpawning) {
                    wasBlocked[i] = false;
                    cycleTimer[i] = 0f;
                    if (explodeCountdown >= 0f) {
                        nearDeathSave();
                    }
                    break;
                }
            }
        }

        boolean allBlockedAtMin = players > 0;
        for (int i = 0; i < players; i++) {
            if (i >= board.getActivePieces().size()) { allBlockedAtMin = false; break; }
            Piece p = board.getActivePieces().get(i);
            if (!p.isBlockedFromSpawning || timeBetweenNextPiece[i] > GameConstants.CYCLE_MIN) {
                allBlockedAtMin = false;
                break;
            }
        }

        if (allBlockedAtMin && !gameEnded) {
            if (explodeCountdown < 0f) {
                explodeCountdown = 0f;
            }
            explodeCountdown += dtSec;
            if (explodeCountdown >= GameConstants.EXPLODE_DURATION) {
                beginGameEnd(false);
            }
        } else if (!allBlockedAtMin && explodeCountdown >= 0f && !gameEnded) {
            explodeCountdown = -1f;
        }
    }

    /**
     * Applies a hold action for a blocked player, with coyote-time support.
     */
    private void applyBlockedHold(int playerId, Board board) {
        if (!canHoldWhileBlocked(playerId)) return;
        if (board.getActivePieces().size() <= playerId) return;

        long now = System.currentTimeMillis();
        byte currentType = board.getActivePieces().get(playerId).type;
        byte effectiveType = (lastCycleSwitchMs[playerId] > 0
                && (now - lastCycleSwitchMs[playerId]) <= GameConstants.COYOTE_MS)
                ? previousCyclePieceId[playerId]
                : currentType;

        byte oldHeld = board.getHeldPieceType();
        board.setHeldPieceType(effectiveType);

        if (oldHeld == 0) {
            board.spawnNextPiece(playerId);
        } else {
            board.spawnHeldPiece(playerId, oldHeld);
        }

        timeBetweenNextPiece[playerId] = GameConstants.CYCLE_START;
        cycleTimer[playerId] = 0f;
        lastHoldUsedMs = System.currentTimeMillis();
        HoldSoundBroadcast hsb = new HoldSoundBroadcast();
        hsb.playerId = (byte) playerId;
        hsb.success = true;
        pendingHoldSounds.add(hsb);
    }

    /**
     * Called when a player's piece is freed during the explode countdown.
     */
    private void nearDeathSave() {
        explodeCountdown = -1f;
    }

    /**
     * Detects win/loss: freezes the end-of-game payload (using the current elapsed time) and
     * schedules {@link #finalizeGameEnd()} to run once the mode-specific grace period elapses
     * (see {@link GameConstants#PUZZLE_GAME_END_GRACE_MS}). Does not touch {@code game}, so the
     * current tick's {@link #sendNetUpdates()} still runs and flushes the final board/particles/
     * sounds queued earlier in this same tick.
     */
    private void beginGameEnd(boolean win) {
        beginGameEnd(win, false);
    }

    private void beginGameEnd(boolean win, boolean disconnected) {
        if (gameEnded) return;
        gameEnded = true;
        pendingWin = win;
        pendingDisconnected = disconnected;
        long graceMs = (gameMode == GameMode.MULTIPLAYER_PUZZLE) ? GameConstants.PUZZLE_GAME_END_GRACE_MS : 0L;
        gameEndGraceUntilMs = System.currentTimeMillis() + graceMs;

        frozenScoreEnd = null;
        frozenPuzzleEnd = null;
        if (gameMode == GameMode.MULTIPLAYER_SCORE) {
            frozenScoreEnd = new ScoreModeEndData();
            frozenScoreEnd.finalScore = totalScore;
            frozenScoreEnd.timeSurvivedMs = System.currentTimeMillis() - gameStartMs;
        } else if (gameMode == GameMode.MULTIPLAYER_PUZZLE) {
            frozenPuzzleElapsedMs = System.currentTimeMillis() - gameStartMs;
            frozenPuzzleEnd = new PuzzleModeEndData();
            frozenPuzzleEnd.timeMs = frozenPuzzleElapsedMs;
            frozenPuzzleEnd.score = (int) (Integer.MAX_VALUE - Math.min(frozenPuzzleElapsedMs, Integer.MAX_VALUE));
        }
    }

    /** Actually tears down the game: broadcasts EndGameBroadcast and stops the game. */
    private void finalizeGameEnd() {
        long score = computeFinalScore();

        GameEndInfo info = new GameEndInfo();
        info.mode = gameMode;
        info.win = pendingWin;
        info.disconnected = pendingDisconnected;
        info.scoreModeEnd = frozenScoreEnd;
        info.puzzleModeEnd = frozenPuzzleEnd;
        info.score = score;
        info.displayScore = computeFinalDisplayScore(score);
        if (frozenScoreEnd != null) {
            info.extraJson = new Json().toJson(frozenScoreEnd);
        } else if (frozenPuzzleEnd != null) {
            info.extraJson = new Json().toJson(frozenPuzzleEnd);
        }

        room.sendEndGame(info);
        stopGame();
    }

    /**
     * Returns the hidden, sortable score for the current gamemode. Always a {@code long}
     * regardless of what the gamemode actually displays to players.
     */
    private long computeFinalScore() {
        switch (gameMode) {
            case MULTIPLAYER_SCORE:
                return totalScore;
            case MULTIPLAYER_PUZZLE:
                return frozenPuzzleEnd != null ? frozenPuzzleEnd.score : 0L;
            default:
                return 0L;
        }
    }

    /**
     * Returns the leaderboard-facing display string for the current gamemode. For
     * MULTIPLAYER_SCORE this is simply the score itself; MULTIPLAYER_PUZZLE instead formats
     * the frozen elapsed time as {@code "m:ss"} (matching the client's EndGameScreen), since a
     * faster time is the meaningful result rather than the sortable score value.
     */
    private String computeFinalDisplayScore(long score) {
        switch (gameMode) {
            case MULTIPLAYER_PUZZLE:
                return frozenPuzzleEnd != null ? formatMinutesSeconds(frozenPuzzleEnd.timeMs) : "0:00";
            default:
                return String.valueOf(score);
        }
    }

    private static String formatMinutesSeconds(long ms) {
        long mins = ms / 60000;
        long secs = (ms % 60000) / 1000;
        return mins + ":" + String.format("%02d", secs);
    }

    /** Called once per tick from {@link #update()}; finalizes the ended game once its grace period elapses. */
    private void checkGameEndGrace() {
        if (!gameEnded || game == null) return;
        if (System.currentTimeMillis() >= gameEndGraceUntilMs) {
            finalizeGameEnd();
        }
    }

    /**
     * Called when a player disconnects mid-game. Synchronized so this can never interleave
     * with an in-flight {@link #update()} call on the room thread (see class-level note on
     * {@link #startGame(GameMode, int, int)}).
     */
    public synchronized void handleDisconnectedPlayer(int id) {
        beginGameEnd(false, true);
    }

    public synchronized void update() {
        deltaTime = (int)(System.currentTimeMillis() - lastUpdateMs);

        switch (gameMode) {
            case NONE:
                break;
            case MULTIPLAYER_SCORE:
                updateScoreMode();
                if (game != null) sendNetUpdates();
                break;
            case MULTIPLAYER_PUZZLE:
                updatePuzzleMode();
                if (game != null) sendNetUpdates();
                break;
        }
        checkGameEndGrace();

        lastUpdateMs = System.currentTimeMillis();
        t++;
    }

    public void updateScoreMode() {
        if (gameEnded) return; // frozen during the grace window; sendNetUpdates() above keeps re-sending the last state
        game.update(deltaTime);
        for (LineClearResult r : game.getAndClearPendingLockResults()) {
            if (r.placed) {
                processPlacement(r);
                if (!r.manual) {
                    hardDropBlockedUntilMs[r.playerId] = System.currentTimeMillis() + GameConstants.HARD_DROP_SUPPRESS_MS;
                }
            }
        }
        if (game.isStarted() && !gameEnded) {
            updateBlockedCycling(deltaTime / 1000f);
            if (System.currentTimeMillis() >= gameEndTargetMs) {
                beginGameEnd(true);
            }
        }
    }

    public void updatePuzzleMode() {
        if (gameEnded) return; // frozen during the grace window; sendNetUpdates() above keeps re-sending the last state
        game.update(deltaTime);
        for (LineClearResult r : game.getAndClearPendingLockResults()) {
            if (r.placed) {
                processPlacement(r);
                if (!r.manual) {
                    hardDropBlockedUntilMs[r.playerId] = System.currentTimeMillis() + GameConstants.HARD_DROP_SUPPRESS_MS;
                }
            }
        }
        if (game.isStarted() && !gameEnded) {
            updateBlockedCycling(deltaTime / 1000f);
            if (!game.getBoards().isEmpty() && !game.getBoards().get(0).hasGarbage()) {
                beginGameEnd(true);
            }
        }
    }

    /** Live puzzle-mode data broadcast every tick: the count-up timer value, frozen once ended. */
    public PuzzleModeData getPuzzleModeData() {
        PuzzleModeData d = new PuzzleModeData();
        d.elapsedMs = gameEnded ? frozenPuzzleElapsedMs
                : (game != null && game.isStarted() ? Math.max(0, System.currentTimeMillis() - gameStartMs) : 0);
        return d;
    }

    public boolean isGameEnded() {
        return gameEnded;
    }

    public void sendNetUpdates() {
        if (t % 2 == 0 && game != null) {
            room.sendNetUpdates();
        }
    }

    public GameHandler getGame() {
        return game;
    }

    public int[] getPiecesPlaced() {
        return piecesPlaced;
    }
}
