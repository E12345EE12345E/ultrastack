package me.ethanchen.game.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.board.Piece;

class ArtifactRollerTest {

    @Test
    void rollProducesOneEffectEntryGroupPerLevel() {
        Random rng = new Random(1);
        Artifact a = ArtifactRoller.roll(Piece.I, 3, 50f, rng);
        assertEquals(Piece.I, a.pieceType);
        assertEquals(3, a.level);
        assertTrue(a.effects.size() >= 3, "each level should contribute at least one effect entry");
    }

    @Test
    void levelUpKeepsPreviousEffectsAndBaseQuality() {
        Random rng = new Random(2);
        Artifact a = ArtifactRoller.roll(Piece.O, 1, 40f, rng);
        int before = a.effects.size();
        float baseQ = a.baseQuality;
        ArtifactRoller.levelUp(a, 2, rng);
        assertEquals(3, a.level);
        assertEquals(baseQ, a.baseQuality);
        assertTrue(a.effects.size() > before);
    }

    @Test
    void qualityTweakStaysWithinTheoreticalBounds() {
        Random rng = new Random(3);
        float base = 10f;
        for (int i = 0; i < 1000; i++) {
            float tweaked = ArtifactRoller.tweakQuality(base, 50f, rng);
            // 5 iterations of *0.9 or *1.1 => bounds of 0.9^5 .. 1.1^5 (~59%..161%)
            assertTrue(tweaked >= base * 0.9f * 0.9f * 0.9f * 0.9f * 0.9f - 1e-4f);
            assertTrue(tweaked <= base * 1.1f * 1.1f * 1.1f * 1.1f * 1.1f + 1e-4f);
        }
    }

    @Test
    void higherBaseQualityBiasesTowardHigherTweakedQuality() {
        Random rngLow = new Random(4);
        Random rngHigh = new Random(4);
        float base = 10f;
        float lowSum = 0f, highSum = 0f;
        int trials = 2000;
        for (int i = 0; i < trials; i++) lowSum += ArtifactRoller.tweakQuality(base, 5f, rngLow);
        for (int i = 0; i < trials; i++) highSum += ArtifactRoller.tweakQuality(base, 95f, rngHigh);
        assertTrue(highSum > lowSum);
    }
}
