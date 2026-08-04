package me.ethanchen.game.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.board.Piece;

class ArtifactRollerTest {

    @Test
    void rollProducesExactlyOneEffectPerLevel() {
        Random rng = new Random(1);
        Artifact a = ArtifactRoller.roll(Piece.I, 3, 50f, rng);
        assertEquals(Piece.I, a.pieceType);
        assertEquals(3, a.level);
        assertEquals(50f, a.baseQuality);
        assertEquals(3, a.effects.size());
    }

    @Test
    void levelUpKeepsPreviousEffectsAndBaseQuality() {
        Random rng = new Random(2);
        Artifact a = ArtifactRoller.roll(Piece.O, 1, 40f, rng);
        assertEquals(1, a.effects.size());
        float baseQ = a.baseQuality;
        ArtifactRoller.levelUp(a, 2, rng);
        assertEquals(3, a.level);
        assertEquals(baseQ, a.baseQuality);
        assertEquals(3, a.effects.size());
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

    @Test
    void exclusiveChancesMatchTablePercentsApproximately() {
        // O table: 45% A, 45% C, 5% E, 5% F — exactly one entry per level.
        int trials = 8000;
        Map<ArtifactEffectType, Integer> counts = new EnumMap<>(ArtifactEffectType.class);
        for (ArtifactEffectType t : ArtifactEffectType.values()) counts.put(t, 0);
        Random rng = new Random(42);
        for (int i = 0; i < trials; i++) {
            Artifact a = ArtifactRoller.roll(Piece.O, 1, 50f, rng);
            assertEquals(1, a.effects.size());
            counts.put(a.effects.get(0).type, counts.get(a.effects.get(0).type) + 1);
        }
        assertApproxPercent(counts.get(ArtifactEffectType.LINE_CLEAR_SCORE), trials, 0.45);
        assertApproxPercent(counts.get(ArtifactEffectType.LINE_CLEAR_METER), trials, 0.45);
        assertApproxPercent(counts.get(ArtifactEffectType.EQUIPPED_LINE_CLEAR_METER), trials, 0.05);
        assertApproxPercent(counts.get(ArtifactEffectType.EQUIPPED_SPIN_METER), trials, 0.05);
        assertEquals(0, counts.get(ArtifactEffectType.SPIN_SCORE));
        assertEquals(0, counts.get(ArtifactEffectType.SPIN_METER));
        assertEquals(0, counts.get(ArtifactEffectType.EQUIPPED_PASSIVE_FILL_SPEED));
    }

    private static void assertApproxPercent(int count, int trials, double expected) {
        double actual = count / (double) trials;
        assertTrue(Math.abs(actual - expected) < 0.02,
                "expected ~" + expected + " got " + actual);
    }
}
