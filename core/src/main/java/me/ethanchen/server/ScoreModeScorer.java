package me.ethanchen.server;

import java.util.Arrays;
import java.util.Random;

import me.ethanchen.game.GameConstants;
import me.ethanchen.game.GameHandler;
import me.ethanchen.game.board.LineClearResult;
import me.ethanchen.game.board.Piece;
import me.ethanchen.game.board.SpinType;
import me.ethanchen.network.packets.s2c.NetParticle;
import me.ethanchen.network.packets.s2c.gamemode.ScoreModeData;

/**
 * Encapsulates all MULTIPLAYER_SCORE scoring state and logic extracted from {@link ServerGame}:
 * glow target, repeat columns, accumulated score, and the per-placement scoring algorithm.
 */
class ScoreModeScorer {

    private long totalScore;
    private int glowPlayerId;
    private int repeatColumn;
    private int repeatColumn2;
    private float[] glowValues;
    private final Random scoreRng = new Random();
    private int players;
    private GameHandler game;

    ScoreModeScorer() {}

    /** Re-initialises scorer for a new game. */
    void reset(int players, GameHandler game) {
        this.players = players;
        this.game = game;
        totalScore = 0;
        glowPlayerId = -1;
        repeatColumn = -1;
        repeatColumn2 = -1;
        glowValues = new float[players];
        Arrays.fill(glowValues, 0.5f);
    }

    long getTotalScore() { return totalScore; }

    // -------------------------------------------------------------------------
    // Per-placement scoring
    // -------------------------------------------------------------------------

    /**
     * Scores the placement, updates glow/repeat-column state, queues score popup particles,
     * updates combo/B2B counters via {@code game}, and ramps gravity.
     * Called only in MULTIPLAYER_SCORE mode.
     */
    void scoreHardDrop(LineClearResult result, PlacementEffects effects) {
        int lines = result.numClearedRows();
        if (lines == 0) {
            game.applyClearToCounters(result);
            return;
        }

        int priorB2b = game.getB2b();
        int priorCombo = game.getCombo();
        int priorComboPlayer = game.getPreviousComboPlayerId();
        boolean eligible = GameHandler.isB2BEligible(result);

        boolean b2bBonus    = eligible && priorB2b >= 1;
        boolean comboBonus  = priorCombo >= 1 && priorComboPlayer != result.playerId;
        boolean glowBonus   = result.playerId == glowPlayerId;
        boolean diffColBonus = !clearedTilesHitRepeatColumn(result);

        long base = baseScore(result.spinType, lines);

        double multiplier = 1.0;
        if (b2bBonus)    multiplier *= GameConstants.B2B_MULTIPLIER;
        if (comboBonus)  multiplier *= GameConstants.COMBO_MULTIPLIER;
        if (glowBonus)   multiplier *= GameConstants.GLOW_MULTIPLIER;
        if (diffColBonus) multiplier *= GameConstants.DIFF_COLUMN_MULTIPLIER;
        long points = Math.round(base * multiplier);
        totalScore += points;

        float cx = result.restingCenterX;
        float cy = result.restingCenterY;

        NetParticle scoreParticle = new NetParticle();
        scoreParticle.boardIndex = 0;
        scoreParticle.kind = NetParticle.KIND_POPUP_SCORE;
        scoreParticle.tileType = result.pieceType;
        scoreParticle.x = cx;
        scoreParticle.y = cy;
        scoreParticle.value = (int) Math.min(points, Integer.MAX_VALUE);
        effects.pendingParticles.add(scoreParticle);

        int bonusBits = (b2bBonus    ? 1 : 0)
                      | (diffColBonus ? 2 : 0)
                      | (comboBonus  ? 4 : 0)
                      | (glowBonus   ? 8 : 0);
        if (bonusBits != 0) {
            NetParticle multParticle = new NetParticle();
            multParticle.boardIndex = 0;
            multParticle.kind = NetParticle.KIND_POPUP_SCORE_MULTIPLIER;
            multParticle.tileType = result.pieceType;
            multParticle.x = cx;
            multParticle.y = cy;
            multParticle.value = bonusBits;
            effects.pendingParticles.add(multParticle);
        }

        if (glowBonus) glowPlayerId = -1;
        if (eligible && players > 1) {
            glowPlayerId = randomOtherPlayer(result.playerId);
        }
        rebuildGlowValues();

        updateRepeatColumns(result);

        game.applyClearToCounters(result);

        int newGravity = game.getGravity();
        for (int i = 0; i < lines; i++) {
            newGravity = (int) Math.max(GameConstants.GRAVITY_FLOOR_MS, newGravity * GameConstants.GRAVITY_RAMP);
        }
        game.setGravity(newGravity);
    }

    ScoreModeData getScoreModeData() {
        ScoreModeData d = new ScoreModeData();
        d.glowingValues = (glowValues != null) ? Arrays.copyOf(glowValues, glowValues.length) : new float[0];
        d.totalScore    = totalScore;
        d.repeatColumn  = repeatColumn;
        d.repeatColumn2 = repeatColumn2;
        return d;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    static long baseScore(SpinType spinType, int lines) {
        switch (spinType) {
            case T_SPIN:
                switch (lines) {
                    case 1: return GameConstants.SCORE_TSPIN_SINGLE;
                    case 2: return GameConstants.SCORE_TSPIN_DOUBLE;
                    case 3: return GameConstants.SCORE_TSPIN_TRIPLE;
                }
                break;
            case T_SPIN_MINI:
                switch (lines) {
                    case 1: return GameConstants.SCORE_TSPIN_MINI_SINGLE;
                    case 2: return GameConstants.SCORE_TSPIN_MINI_DOUBLE;
                }
                break;
            case ALL_SPIN:
                switch (lines) {
                    case 1: return GameConstants.SCORE_ALL_SPIN_SINGLE;
                    case 2: return GameConstants.SCORE_ALL_SPIN_DOUBLE;
                    case 3: return GameConstants.SCORE_ALL_SPIN_TRIPLE;
                    case 4: return GameConstants.SCORE_ALL_SPIN_TETRIS;
                }
                break;
            case SMALL_SPIN:
                switch (lines) {
                    case 1: return GameConstants.SCORE_SMALL_SPIN_SINGLE;
                    case 2: return GameConstants.SCORE_SMALL_SPIN_DOUBLE;
                    case 3: return GameConstants.SCORE_SMALL_SPIN_TRIPLE;
                }
                break;
            default:
                break;
        }
        switch (lines) {
            case 1: return GameConstants.SCORE_SINGLE;
            case 2: return GameConstants.SCORE_DOUBLE;
            case 3: return GameConstants.SCORE_TRIPLE;
            case 4: return GameConstants.SCORE_TETRIS;
        }
        return 0;
    }

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
}
