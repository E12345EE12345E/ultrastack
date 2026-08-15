package me.ethanchen.server;

import java.util.Arrays;

import me.ethanchen.game.GameHandler;
import me.ethanchen.game.board.LineClearResult;
import me.ethanchen.network.packets.s2c.gamemode.PveModeData;
import me.ethanchen.network.packets.s2c.gamemode.ScoreModeData;
import me.ethanchen.server.ScoreFormulas.BoardScoreState;

/**
 * One board's PvE scoring state. Uses the same placement formulas as score mode via
 * {@link ScoreFormulas}, but owns its own glow/repeat/board-score state and never participates
 * in {@link ScoreModeData} / the four-minute score-mode timer.
 */
class PveScorer {

    private final BoardScoreState state = new BoardScoreState();
    private int boardIndex;
    private GameHandler game;
    /** Non-null when characters/artifacts are active in PvE. */
    private CharacterScoreBonusProvider bonusProvider;

    PveScorer() {}

    void setBonusProvider(CharacterScoreBonusProvider bonusProvider) {
        this.bonusProvider = bonusProvider;
    }

    void reset(int boardIndex, int[] boardSlots, GameHandler game) {
        this.boardIndex = boardIndex;
        this.game = game;
        state.reset(boardSlots);
    }

    long getTotalScore() { return state.totalScore; }

    long scoreHardDrop(LineClearResult result, PlacementEffects effects) {
        // PvE keeps a fixed gravity unless a section/boss env overrides it — no clear-based ramp.
        return ScoreFormulas.scoreHardDrop(state, game, boardIndex, result, effects, bonusProvider, false, false);
    }

    long scoreFallingClear(LineClearResult result, PlacementEffects effects) {
        return ScoreFormulas.scoreFallingClear(state, game, boardIndex, result, effects, false);
    }

    /** Copies board-local scoring visuals into a live {@link PveModeData} snapshot. */
    void populateBoardVisuals(PveModeData out) {
        if (out == null) return;
        out.glowingValues = (state.glowValues != null)
                ? Arrays.copyOf(state.glowValues, state.glowValues.length) : new float[0];
        out.repeatColumn = state.repeatColumn;
        out.repeatColumn2 = state.repeatColumn2;
        out.boardScore = state.totalScore;
    }
}
