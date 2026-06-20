package me.ethanchen.headless;

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
import me.ethanchen.network.packets.s2c.NetParticle;
import me.ethanchen.network.packets.s2c.gamemode.ScoreModeData;

public class ServerGame {
    private volatile boolean inProgress; public boolean isInProgress() { return inProgress; }
    private volatile long lastUpdateMs;
    private int deltatime;
    private GameMode gamemode;
    private int players;
    private GameHandler game;
    private ServerApp app;
    private int t;
    private int[] highestMoveId;
    private final ArrayList<NetParticle> pendingParticles = new ArrayList<>();
    private int[] piecesPlaced;

    // Hold state
    private long lastHoldUsedMs = 0;
    private static final long HOLD_GLOBAL_LOCK_MS = 1000;

    // MULTIPLAYER_SCORE mode state
    private long totalScore;
    private int glowPlayerId;
    private int repeatColumn;
    private int repeatColumn2;
    private float[] glowValues;
    private final Random scoreRng = new Random();

    public ServerGame(ServerApp app) {
        inProgress = false;
        gamemode = GameMode.NONE;
        this.app = app;
    }

    public boolean startGame(GameMode gamemode, int players, int msToStart) {
        if (inProgress) return false;
        // start game depending on gamemode
        inProgress = true;
        lastUpdateMs = System.currentTimeMillis();
        this.gamemode = gamemode;
        this.players = players;
        this.game = new GameHandler(players);
        this.game.init(gamemode, msToStart);
        this.highestMoveId = new int[players];
        this.piecesPlaced = new int[players];
        Arrays.fill(this.highestMoveId, -1);
        // Hold state reset
        lastHoldUsedMs = 0;
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
                        LineClearResult result = board.hardDrop(playerId);
                        if (result != null && result.placed) {
                            piecesPlaced[playerId]++;
                            switch (gamemode) {
                                case MULTIPLAYER_SCORE:
                                    scoreHardDrop(result);
                                    break;
                                default:
                                    break;
                            }
                            pendingParticles.addAll(resultToParticles(result));
                        }
                    } else if (move == MoveType.HOLD) {
                        if (board.useHold(playerId)) {
                            lastHoldUsedMs = System.currentTimeMillis();
                        }
                    } else {
                        board.applyMove(playerId, move);
                    }
                }
            }
        }
    }

    /**
     * Returns the accumulated particle events and clears the internal list.
     * Called by {@link ServerApp#sendNetUpdates()} each broadcast cycle.
     */
    public ArrayList<NetParticle> getAndClearPendingParticles() {
        if (pendingParticles.isEmpty()) return null;
        ArrayList<NetParticle> copy = new ArrayList<>(pendingParticles);
        pendingParticles.clear();
        return copy;
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
        // NONE (or any spin with unspecified line count falls through here)
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
        // Skip over the excluded player id
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
        long now = System.currentTimeMillis();
        boolean globalLock = lastHoldUsedMs > 0 && (now - lastHoldUsedMs) < HOLD_GLOBAL_LOCK_MS;
        Board board = game.getBoards().get(0);
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
     * Translates a {@link LineClearResult} into {@link NetParticle} spawn events.
     * <ul>
     *   <li>Placed cells → FLASH (kind=0) white particles at each locked mino.</li>
     *   <li>Cleared cells → TILE_BREAK (kind=1) particles of the cleared tile's color.</li>
     *   <li>Broken cells (landed on allowedTiles=false) → TILE_BREAK particles.</li>
     * </ul>
     */
    private ArrayList<NetParticle> resultToParticles(LineClearResult result) {
        ArrayList<NetParticle> out = new ArrayList<>();
        // Flash at each successfully locked cell
        for (int[] cell : result.placedCells) {
            NetParticle np = new NetParticle();
            np.boardIndex = 0;
            np.kind = 0; // FLASH
            np.tileType = result.pieceType;
            np.x = cell[0];
            np.y = cell[1];
            out.add(np);
        }
        // Tile-break for every cleared cell
        for (int[] cell : result.clearedCells) {
            NetParticle np = new NetParticle();
            np.boardIndex = 0;
            np.kind = 1; // TILE_BREAK
            np.tileType = (byte) cell[2];
            np.x = cell[0];
            np.y = cell[1];
            out.add(np);
        }
        // Tile-break for cells that landed on a wall
        for (int[] cell : result.brokenCells) {
            NetParticle np = new NetParticle();
            np.boardIndex = 0;
            np.kind = 1; // TILE_BREAK
            np.tileType = (byte) cell[2];
            np.x = cell[0];
            np.y = cell[1];
            out.add(np);
        }
        return out;
    }

    public void handleDisconnectedPlayer(int id) {
        stopGame(); // for now, because maybe implement temporary bot player or something later
    }

    public void update() {
        deltatime = (int)(System.currentTimeMillis() - lastUpdateMs);

        switch (gamemode) {
            case NONE:
                break;
            case MULTIPLAYER_SCORE:
                updateScoreMode();
                sendNetUpdates();
                break;
        }

        lastUpdateMs = System.currentTimeMillis();
        t++;
    }

    public void updateScoreMode() { // called if gamemode is a scoring type mode
        game.update(deltatime);
    }

    public void sendNetUpdates() {
        if (t % 2 == 0) {
            app.sendNetUpdates();
        }
    }

    public GameHandler getGame() {
        return game;
    }

    public int[] getPiecesPlaced() {
        return piecesPlaced;
    }
}
