package me.ethanchen.server;

import java.util.Arrays;

import me.ethanchen.game.GameHandler;
import me.ethanchen.game.board.LineClearResult;
import me.ethanchen.game.board.SpinType;
import me.ethanchen.network.packets.s2c.gamemode.ScoreModeData;
import me.ethanchen.server.ScoreFormulas.BoardScoreState;

/**
 * One board's MULTIPLAYER_SCORE / CHARACTER_SCORE scoring state. Uses {@link ScoreFormulas}
 * for the shared placement math; PvE has its own {@link PveScorer}.
 */
class ScoreModeScorer {

    private final BoardScoreState state = new BoardScoreState();
    private int boardIndex;
    private GameHandler game;
    /** Non-null only for CHARACTER_ modes. */
    private CharacterScoreBonusProvider bonusProvider;

    ScoreModeScorer() {}

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
        return ScoreFormulas.scoreHardDrop(state, game, boardIndex, result, effects, bonusProvider, true);
    }

    long scoreFallingClear(LineClearResult result, PlacementEffects effects) {
        return ScoreFormulas.scoreFallingClear(state, game, boardIndex, result, effects, true);
    }

    /** Board-local data; {@code totalScore} is filled in by {@link ServerGame} with the session aggregate. */
    ScoreModeData getScoreModeData() {
        ScoreModeData d = new ScoreModeData();
        d.glowingValues = (state.glowValues != null)
                ? Arrays.copyOf(state.glowValues, state.glowValues.length) : new float[0];
        d.boardScore = state.totalScore;
        d.repeatColumn = state.repeatColumn;
        d.repeatColumn2 = state.repeatColumn2;
        return d;
    }

    /** Kept for callers/tests that historically called {@code ScoreModeScorer.baseScore}. */
    static long baseScore(SpinType spinType, int lines) {
        return ScoreFormulas.baseScore(spinType, lines);
    }

    static long baseScore(SpinType spinType, int lines, byte pieceType) {
        return ScoreFormulas.baseScore(spinType, lines, pieceType);
    }
}
