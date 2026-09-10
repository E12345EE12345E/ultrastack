package me.ethanchen.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.board.SpinType;

class ScoreFormulasTest {

    @Test
    void scoresAdditionalLinesBeyondQuadBeforeMultipliers() {
        assertEquals(800L, ScoreFormulas.baseScore(SpinType.NONE, 4));
        assertEquals(1000L, ScoreFormulas.baseScore(SpinType.NONE, 5));
        assertEquals(2000L, ScoreFormulas.baseScore(SpinType.NONE, 10));
    }
}
