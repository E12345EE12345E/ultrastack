package me.ethanchen.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import me.ethanchen.game.GameConstants;
import me.ethanchen.game.GameHandler;
import me.ethanchen.game.board.LineClearResult;
import me.ethanchen.game.board.Piece;
import me.ethanchen.game.board.SpinType;
import me.ethanchen.network.packets.s2c.NetParticle;

/**
 * Shared scoring math and placement helpers used by score-mode and PvE board scorers.
 * Mode-specific state (glow target, repeat columns, board total) lives on each scorer;
 * only the formulas and side-effect helpers are shared here.
 */
final class ScoreFormulas {
    private ScoreFormulas() {}

    static long baseScore(SpinType spinType, int lines) {
        return baseScore(spinType, lines, (byte) 0);
    }

    static long baseScore(SpinType spinType, int lines, byte pieceType) {
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
        if (pieceType == Piece.I3 && lines == 3) return GameConstants.SCORE_I3_TRIPLE;
        switch (lines) {
            case 1: return GameConstants.SCORE_SINGLE;
            case 2: return GameConstants.SCORE_DOUBLE;
            case 3: return GameConstants.SCORE_TRIPLE;
            case 4: return GameConstants.SCORE_TETRIS;
        }
        return 0;
    }

    /**
     * Applies hard-drop scoring for modes that use the shared score formula (glow / B2B / combo /
     * diff-column multipliers, optional character bonus, popup particles). When
     * {@code rampGravityOnClear} is true (score modes), gravity ramps after each clear; PvE
     * passes false so gravity stays fixed unless a section/boss env sets it.
     *
     * @param spawnMultiplierPopup when false (PvE), only the {@code +N} score popup is queued
     * @return points awarded (0 when no lines cleared)
     */
    static long scoreHardDrop(BoardScoreState state, GameHandler game, int boardIndex,
                              LineClearResult result, PlacementEffects effects,
                              CharacterScoreBonusProvider bonusProvider,
                              boolean rampGravityOnClear, boolean spawnMultiplierPopup) {
        int lines = result.numClearedRows();
        if (lines == 0) {
            game.applyClearToCounters(result);
            return 0L;
        }

        int priorB2b = game.getB2b(boardIndex);
        int priorCombo = game.getCombo(boardIndex);
        int priorComboPlayer = game.getPreviousComboPlayerId(boardIndex);
        boolean eligible = GameHandler.isB2BEligible(result);

        boolean b2bBonus = eligible && priorB2b >= 1;
        boolean comboBonus = priorCombo >= 1 && priorComboPlayer != result.playerId;
        boolean glowBonus = result.playerId == state.glowPlayerId;
        boolean diffColBonus = !clearedTilesHitRepeatColumn(state, result);

        long base = baseScore(result.spinType, lines, result.pieceType);
        double multiplier = 1.0;
        if (b2bBonus) multiplier *= GameConstants.B2B_MULTIPLIER;
        if (comboBonus) multiplier *= GameConstants.COMBO_MULTIPLIER;
        if (glowBonus) multiplier *= GameConstants.GLOW_MULTIPLIER;
        if (diffColBonus) multiplier *= GameConstants.DIFF_COLUMN_MULTIPLIER;
        if (bonusProvider != null) {
            float artifactBonusPercent = bonusProvider.scoreBonusPercent(
                    result.playerId, result.pieceType, lines > 0, result.spinType != SpinType.NONE);
            multiplier *= (1.0 + artifactBonusPercent / 100.0);
        }
        long points = Math.round(base * multiplier);
        if (result.allClear) points += GameConstants.SCORE_ALL_CLEAR_BONUS;
        state.totalScore += points;

        int bonusBits = (b2bBonus ? 1 : 0)
                | (diffColBonus ? 2 : 0)
                | (comboBonus ? 4 : 0)
                | (glowBonus ? 8 : 0);
        queueScorePopups(effects, boardIndex, result, points, spawnMultiplierPopup ? bonusBits : 0);

        if (glowBonus) state.glowPlayerId = -1;
        if (eligible && state.boardSlots.length > 1) {
            state.glowPlayerId = randomOtherPlayer(state, result.playerId);
        }
        rebuildGlowValues(state);
        updateRepeatColumns(state, result);

        game.applyClearToCounters(result);
        if (rampGravityOnClear) {
            game.setGravity(boardIndex, rampGravity(game.getGravity(boardIndex), lines));
        }
        return points;
    }

    /**
     * Flat falling-column clear scoring (no combo/B2B/glow/diff-column multipliers).
     * When {@code rampGravityOnClear} is true (score modes), gravity ramps after each clear.
     *
     * @return points awarded (0 when no lines cleared)
     */
    static long scoreFallingClear(BoardScoreState state, GameHandler game, int boardIndex,
                                  LineClearResult result, PlacementEffects effects,
                                  boolean rampGravityOnClear) {
        int lines = result.numClearedRows();
        if (lines == 0) {
            game.applyClearToCounters(result);
            return 0L;
        }

        long points = GameConstants.SCORE_FALLING_PER_LINE * lines;
        if (result.allClear) points += GameConstants.SCORE_ALL_CLEAR_BONUS;
        state.totalScore += points;

        queueScorePopups(effects, boardIndex, result, points, 0);
        game.applyClearToCounters(result);
        if (rampGravityOnClear) {
            game.setGravity(boardIndex, rampGravity(game.getGravity(boardIndex), lines));
        }
        return points;
    }

    static int rampGravity(int currentGravityMs, int lines) {
        int newGravity = currentGravityMs;
        for (int i = 0; i < lines; i++) {
            newGravity = (int) Math.max(GameConstants.GRAVITY_FLOOR_MS,
                    newGravity * GameConstants.GRAVITY_RAMP);
        }
        return newGravity;
    }

    static void queueScorePopups(PlacementEffects effects, int boardIndex, LineClearResult result,
                                 long points, int bonusBits) {
        float cx = result.restingCenterX;
        float cy = result.restingCenterY;

        NetParticle scoreParticle = new NetParticle();
        scoreParticle.boardIndex = (byte) boardIndex;
        scoreParticle.kind = NetParticle.KIND_POPUP_SCORE;
        scoreParticle.tileType = result.pieceType;
        scoreParticle.x = cx;
        scoreParticle.y = cy;
        scoreParticle.value = (int) Math.min(points, Integer.MAX_VALUE);
        effects.pendingParticles.add(scoreParticle);

        if (bonusBits != 0) {
            NetParticle multParticle = new NetParticle();
            multParticle.boardIndex = (byte) boardIndex;
            multParticle.kind = NetParticle.KIND_POPUP_SCORE_MULTIPLIER;
            multParticle.tileType = result.pieceType;
            multParticle.x = cx;
            multParticle.y = cy;
            multParticle.value = bonusBits;
            effects.pendingParticles.add(multParticle);
        }
    }

    static boolean clearedTilesHitRepeatColumn(BoardScoreState state, LineClearResult result) {
        if (state.repeatColumn == -1 && state.repeatColumn2 == -1) return false;
        for (int[] cols : result.filledColumnsPerClearedRow) {
            for (int col : cols) {
                if (col == state.repeatColumn || col == state.repeatColumn2) return true;
            }
        }
        return false;
    }

    static void updateRepeatColumns(BoardScoreState state, LineClearResult result) {
        float cx = result.restingCenterX;
        byte type = result.pieceType;
        byte rot = result.pieceRotation;
        if (type == Piece.I && (rot == 0 || rot == 2)) {
            state.repeatColumn = (int) Math.floor(cx - 0.5f);
            state.repeatColumn2 = (int) Math.floor(cx + 0.5f);
        } else if (type == Piece.O) {
            state.repeatColumn = (int) Math.floor(cx - 0.5f);
            state.repeatColumn2 = (int) Math.floor(cx + 0.5f);
        } else if (type == Piece.I && rot == 1) {
            state.repeatColumn = (int) Math.floor(cx + 0.5f);
            state.repeatColumn2 = -1;
        } else if (type == Piece.I && rot == 3) {
            state.repeatColumn = (int) Math.floor(cx - 0.5f);
            state.repeatColumn2 = -1;
        } else {
            state.repeatColumn = (int) Math.floor(cx);
            state.repeatColumn2 = -1;
        }
    }

    static int randomOtherPlayer(BoardScoreState state, int excludeId) {
        if (state.boardSlots.length <= 1) return -1;
        ArrayList<Integer> others = new ArrayList<>(state.boardSlots.length - 1);
        for (int slot : state.boardSlots) {
            if (slot != excludeId) others.add(slot);
        }
        if (others.isEmpty()) return -1;
        return others.get(state.rng.nextInt(others.size()));
    }

    static void rebuildGlowValues(BoardScoreState state) {
        if (state.glowValues == null || state.glowValues.length != state.boardSlots.length) {
            state.glowValues = new float[state.boardSlots.length];
        }
        Arrays.fill(state.glowValues, 0.25f);
        int localIndex = localIndexOf(state, state.glowPlayerId);
        if (localIndex >= 0) state.glowValues[localIndex] = 2f;
    }

    private static int localIndexOf(BoardScoreState state, int globalSlot) {
        for (int i = 0; i < state.boardSlots.length; i++) {
            if (state.boardSlots[i] == globalSlot) return i;
        }
        return -1;
    }

    /** Mutable per-board scoring state shared by score-mode and PvE scorers. */
    static final class BoardScoreState {
        long totalScore;
        int glowPlayerId = -1;
        int repeatColumn = -1;
        int repeatColumn2 = -1;
        float[] glowValues = new float[0];
        int[] boardSlots = new int[0];
        final Random rng = new Random();

        void reset(int[] boardSlots) {
            this.boardSlots = boardSlots != null ? boardSlots : new int[0];
            totalScore = 0;
            glowPlayerId = -1;
            repeatColumn = -1;
            repeatColumn2 = -1;
            glowValues = new float[this.boardSlots.length];
            Arrays.fill(glowValues, 0.5f);
        }
    }
}
