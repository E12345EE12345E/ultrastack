package me.ethanchen.game.progression;

import java.util.Random;
import java.util.UUID;

/**
 * Rolls new {@link Artifact} instances and additional levels on existing ones, per the acquisition
 * math in implementation.md, Parts 1–3.
 */
public final class ArtifactRoller {

    private static final int QUALITY_TWEAK_ITERATIONS = 5;
    private static final float QUALITY_TWEAK_UP = 1.1f;
    private static final float QUALITY_TWEAK_DOWN = 0.9f;

    private ArtifactRoller() {}

    /** Rolls a brand new artifact of the given type, level, and hidden base quality. */
    public static Artifact roll(byte pieceType, int level, float baseQuality, Random rng) {
        Artifact artifact = new Artifact(UUID.randomUUID().toString(), pieceType, level, baseQuality);
        for (int i = 0; i < level; i++) {
            rollAndAppendLevel(artifact, rng);
        }
        return artifact;
    }

    /**
     * Rolls {@code additionalLevels} exclusive effect picks onto an existing artifact and bumps
     * its level accordingly, keeping its previous effects and base quality (direct level-up,
     * Part 1).
     */
    public static void levelUp(Artifact artifact, int additionalLevels, Random rng) {
        for (int i = 0; i < additionalLevels; i++) {
            rollAndAppendLevel(artifact, rng);
        }
        artifact.level += additionalLevels;
    }

    /**
     * One level = exactly one mutually exclusive pick from the type's effect table, then a
     * quality tweak against the artifact's base quality.
     */
    private static void rollAndAppendLevel(Artifact artifact, Random rng) {
        ArtifactTables.Chance chance = ArtifactTables.rollChance(artifact.pieceType, rng);
        float quality = tweakQuality(chance.quality, artifact.baseQuality, rng);
        artifact.effects.add(new ArtifactEffect(chance.effect, quality));
    }

    /**
     * Applies the base-quality tweak: 5 independent 0-100 rolls, each multiplying {@code quality}
     * by 1.1 on success (roll &lt; baseQuality) or 0.9 on failure. Yields a range of roughly 59%-161%
     * of the untweaked quality.
     */
    static float tweakQuality(float quality, float baseQuality, Random rng) {
        float result = quality;
        for (int i = 0; i < QUALITY_TWEAK_ITERATIONS; i++) {
            float roll = rng.nextFloat() * 100f;
            result *= (roll < baseQuality) ? QUALITY_TWEAK_UP : QUALITY_TWEAK_DOWN;
        }
        return result;
    }
}
