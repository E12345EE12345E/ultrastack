package me.ethanchen.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.board.Piece;
import me.ethanchen.game.board.SpinType;

/**
 * Characterization test for {@link ScoreModeScorer}'s base-score table.
 * {@code baseScore} is now package-private static on {@link ScoreModeScorer} and can be
 * called directly from this same-package test without reflection.
 */
class ServerGameScoringTest {

    private static long baseScore(SpinType spinType, int lines) {
        return ScoreModeScorer.baseScore(spinType, lines);
    }

    @Test
    void standardClears() throws Exception {
        assertEquals(0, baseScore(SpinType.NONE, 0));
        assertEquals(100, baseScore(SpinType.NONE, 1));
        assertEquals(200, baseScore(SpinType.NONE, 2));
        assertEquals(300, baseScore(SpinType.NONE, 3));
        assertEquals(800, baseScore(SpinType.NONE, 4));
    }

    @Test
    void tSpinClears() throws Exception {
        assertEquals(400, baseScore(SpinType.T_SPIN, 1));
        assertEquals(800, baseScore(SpinType.T_SPIN, 2));
        assertEquals(1200, baseScore(SpinType.T_SPIN, 3));
    }

    @Test
    void tSpinMiniClears() throws Exception {
        assertEquals(200, baseScore(SpinType.T_SPIN_MINI, 1));
        assertEquals(800, baseScore(SpinType.T_SPIN_MINI, 2));
    }

    @Test
    void allSpinClears() throws Exception {
        assertEquals(150, baseScore(SpinType.ALL_SPIN, 1));
        assertEquals(300, baseScore(SpinType.ALL_SPIN, 2));
        assertEquals(450, baseScore(SpinType.ALL_SPIN, 3));
        assertEquals(800, baseScore(SpinType.ALL_SPIN, 4));
    }

    @Test
    void smallSpinClears() throws Exception {
        assertEquals(200, baseScore(SpinType.SMALL_SPIN, 1));
        assertEquals(400, baseScore(SpinType.SMALL_SPIN, 2));
        assertEquals(600, baseScore(SpinType.SMALL_SPIN, 3));
    }

    @Test
    void i3TripleScoresLikeMiniTetris() {
        assertEquals(600, ScoreModeScorer.baseScore(SpinType.NONE, 3, Piece.I3));
        // Non-I3 triples still use the standard triple value.
        assertEquals(300, ScoreModeScorer.baseScore(SpinType.NONE, 3, Piece.T));
    }
}
