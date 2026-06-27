package me.ethanchen.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import me.ethanchen.game.GameHandler;
import me.ethanchen.game.GameMode;
import me.ethanchen.game.board.Board;
import me.ethanchen.game.board.LineClearResult;
import me.ethanchen.game.board.MoveType;
import me.ethanchen.game.board.Piece;
import me.ethanchen.game.board.SpinType;
import me.ethanchen.network.packets.s2c.HoldSoundBroadcast;
import me.ethanchen.network.packets.s2c.NetParticle;
import me.ethanchen.network.packets.s2c.ParticleSpawner;
import me.ethanchen.network.packets.s2c.PlacementSoundBroadcast;
import me.ethanchen.network.packets.s2c.gamemode.ScoreModeData;
import me.ethanchen.network.packets.s2c.gamemode.ScoreModeEndData;

public class ServerGame {
    private volatile boolean inProgress; public boolean isInProgress() { return inProgress; }
    private volatile long lastUpdateMs;
    private int deltatime;
    private GameMode gamemode;
    private int players;
    private GameHandler game;
    private GameRoomContext room;
    private int t;
    private int[] highestMoveId;
    private final ArrayList<NetParticle> pendingParticles = new ArrayList<>();
    private final ArrayList<ParticleSpawner> pendingSpawners = new ArrayList<>();
    private final ArrayList<PlacementSoundBroadcast> pendingPlacementSounds = new ArrayList<>();
    private final ArrayList<HoldSoundBroadcast> pendingHoldSounds = new ArrayList<>();
    private int[] piecesPlaced;

    // Hold state
    private long lastHoldUsedMs = 0;
    private static final long HOLD_GLOBAL_LOCK_MS = 1000;
    private static final long HARD_DROP_SUPPRESS_MS = 250L;

    // Per-player hard-drop suppression after auto-lock
    private long[] hardDropBlockedUntilMs;

    // Blocked-spawn cycling constants
    private static final float CYCLE_START       = 1.0f;
    private static final float CYCLE_MULT        = 0.8f;
    private static final float CYCLE_MIN         = 0.25f;
    private static final long  COYOTE_MS         = 50L;
    private static final float EXPLODE_DURATION  = 2.0f;
    private static final float EXPLODE_MIN_INTERVAL = 0.1f;

    // Per-player blocked-cycling state (re-initialized in startGame)
    private float[]  timeBetweenNextPiece;
    private float[]  cycleTimer;
    private long[]   lastCycleSwitchMs;
    private byte[]   previousCyclePieceId;
    private boolean[] wasBlocked;

    // Explode / end-game state
    private float   explodeCountdown = -1f;
    private boolean gameEnded        = false;

    // Timer state
    private long gameStartMs;
    private long gameEndTargetMs;
    private static final long TIMER_DURATION_MS = 4L * 60 * 1000;

    // MULTIPLAYER_SCORE mode state
    private long totalScore;
    private int glowPlayerId;
    private int repeatColumn;
    private int repeatColumn2;
    private float[] glowValues;
    private final Random scoreRng = new Random();

    public ServerGame(GameRoomContext room) {
        inProgress = false;
        gamemode = GameMode.NONE;
        this.room = room;
    }

    public boolean startGame(GameMode gamemode, int players, int msToStart) {
        if (inProgress) return false;
        inProgress = true;
        lastUpdateMs = System.currentTimeMillis();
        this.gamemode = gamemode;
        this.players = players;
        this.game = new GameHandler(players);
        this.game.init(gamemode, msToStart);
        gameStartMs     = System.currentTimeMillis() + msToStart;
        gameEndTargetMs = gameStartMs + TIMER_DURATION_MS;
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
        Arrays.fill(timeBetweenNextPiece, CYCLE_START);
        explodeCountdown = -1f;
        gameEnded        = false;
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

    public void stopGame() {
        this.gamemode = GameMode.NONE;
        this.game = null;
        this.players = 0;
        this.highestMoveId = null;
        inProgress = false;
    }

    public int getHighestMoveId(int playerId) {
        if (highestMoveId == null || playerId < 0 || playerId >= highestMoveId.length) return -1;
        return highestMoveId[playerId];
    }

    public void applyMoves(int playerId, int[] ids, byte[] types) {
        if (!inProgress || game == null || ids == null || types == null) return;
        if (playerId < 0 || playerId >= players) return;
        if (game.getBoards().isEmpty()) return;
        Board board = game.getBoards().get(0);
        if (board.getActivePieces().size() <= playerId) return;
        MoveType[] moveValues = MoveType.values();
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] > highestMoveId[playerId]) {
                highestMoveId[playerId] = ids[i];
                if (types[i] >= 0 && types[i] < moveValues.length) {
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
                                hardDropBlockedUntilMs[playerId] = System.currentTimeMillis() + HARD_DROP_SUPPRESS_MS;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Shared post-placement logic: increments the placement counter, scores the result,
     * and queues particles. Used by hard drops, movement-overflow locks, and timer locks.
     */
    private void processPlacement(LineClearResult result) {
        piecesPlaced[result.playerId]++;
        int priorCombo = game.getCombo();
        switch (gamemode) {
            case MULTIPLAYER_SCORE:
                scoreHardDrop(result);
                break;
            default:
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
        if (b2bBonus)    multiplier *= 1.25;
        if (comboBonus)  multiplier *= 1.5;
        if (glowBonus)   multiplier *= 2.0;
        if (diffColBonus) multiplier *= 1.2;
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
            newGravity = (int) Math.max(50, newGravity * 0.95);
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
        boolean globalLock = lastHoldUsedMs > 0 && (now - lastHoldUsedMs) < HOLD_GLOBAL_LOCK_MS;
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
            return CYCLE_MIN + (EXPLODE_MIN_INTERVAL - CYCLE_MIN) * frac;
        }
        return timeBetweenNextPiece[i];
    }

    /**
     * Returns true when player {@code i}'s blocked piece may be held.
     */
    public boolean canHoldWhileBlocked(int i) {
        if (timeBetweenNextPiece == null || i < 0 || i >= players) return false;
        return timeBetweenNextPiece[i] <= CYCLE_MIN && explodeCountdown < 0f;
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
                timeBetweenNextPiece[i] = CYCLE_START;
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
                timeBetweenNextPiece[i] = Math.max(CYCLE_MIN, timeBetweenNextPiece[i] * CYCLE_MULT);
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
            if (!p.isBlockedFromSpawning || timeBetweenNextPiece[i] > CYCLE_MIN) {
                allBlockedAtMin = false;
                break;
            }
        }

        if (allBlockedAtMin && !gameEnded) {
            if (explodeCountdown < 0f) {
                explodeCountdown = 0f;
            }
            explodeCountdown += dtSec;
            if (explodeCountdown >= EXPLODE_DURATION) {
                triggerEndGame(false);
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
                && (now - lastCycleSwitchMs[playerId]) <= COYOTE_MS)
                ? previousCyclePieceId[playerId]
                : currentType;

        byte oldHeld = board.getHeldPieceType();
        board.setHeldPieceType(effectiveType);

        if (oldHeld == 0) {
            board.spawnNextPiece(playerId);
        } else {
            board.spawnHeldPiece(playerId, oldHeld);
        }

        timeBetweenNextPiece[playerId] = CYCLE_START;
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

    /** Fires the end-game sequence: broadcasts EndGameBroadcast and stops the game. */
    private void triggerEndGame(boolean win) {
        triggerEndGame(win, false);
    }

    /** Fires the end-game sequence with an optional disconnect flag. */
    private void triggerEndGame(boolean win, boolean disconnected) {
        if (gameEnded) return;
        gameEnded = true;
        ScoreModeEndData scoreEnd = null;
        if (gamemode == GameMode.MULTIPLAYER_SCORE) {
            scoreEnd = new ScoreModeEndData();
            scoreEnd.finalScore = totalScore;
            scoreEnd.timeSurvivedMs = System.currentTimeMillis() - gameStartMs;
        }
        room.sendEndGame(win, scoreEnd, disconnected);
        stopGame();
    }

    public void handleDisconnectedPlayer(int id) {
        triggerEndGame(false, true);
    }

    public void update() {
        deltatime = (int)(System.currentTimeMillis() - lastUpdateMs);

        switch (gamemode) {
            case NONE:
                break;
            case MULTIPLAYER_SCORE:
                updateScoreMode();
                if (game != null) sendNetUpdates();
                break;
        }

        lastUpdateMs = System.currentTimeMillis();
        t++;
    }

    public void updateScoreMode() {
        game.update(deltatime);
        for (LineClearResult r : game.getAndClearPendingLockResults()) {
            if (r.placed) {
                processPlacement(r);
                if (!r.manual) {
                    hardDropBlockedUntilMs[r.playerId] = System.currentTimeMillis() + HARD_DROP_SUPPRESS_MS;
                }
            }
        }
        if (game.isStarted() && !gameEnded) {
            updateBlockedCycling(deltatime / 1000f);
            if (System.currentTimeMillis() >= gameEndTargetMs) {
                triggerEndGame(true);
            }
        }
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
