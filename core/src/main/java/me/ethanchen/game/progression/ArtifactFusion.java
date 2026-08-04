package me.ethanchen.game.progression;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Implements artifact fusion: five same-level artifacts are consumed to produce one new artifact
 * of the same level (or one higher on a quality overflow), per implementation.md, Part 2.
 */
public final class ArtifactFusion {

    private static final int FUSION_INPUT_COUNT = 5;
    private static final float OVERFLOW_THRESHOLD = 100f;
    private static final float OVERFLOW_REROLL_MIN = 40f;
    private static final float OVERFLOW_REROLL_MAX = 80f;
    private static final float TYPE_MATCH_CHANCE = 0.2f;

    private ArtifactFusion() {}

    public static final class Result {
        public final Artifact artifact;
        public Result(Artifact artifact) { this.artifact = artifact; }
    }

    /**
     * Fuses exactly five same-level artifacts. Throws {@link IllegalArgumentException} if the
     * precondition (count == 5, all same level) is violated -- callers should validate ownership
     * and non-equipped status separately before calling this.
     */
    public static Result fuse(List<Artifact> inputs, Random rng) {
        if (inputs == null || inputs.size() != FUSION_INPUT_COUNT) {
            throw new IllegalArgumentException("Fusion requires exactly " + FUSION_INPUT_COUNT + " artifacts");
        }
        int level = inputs.get(0).level;
        for (Artifact a : inputs) {
            if (a.level != level) {
                throw new IllegalArgumentException("All fused artifacts must be the same level");
            }
        }

        float sum = 0f;
        for (Artifact a : inputs) {
            float mult = (rng.nextBoolean()) ? 2.0f : 0.8f;
            sum += a.baseQuality * mult;
        }
        float baseValue = sum / FUSION_INPUT_COUNT;

        int outputLevel = level;
        if (baseValue > OVERFLOW_THRESHOLD) {
            baseValue = OVERFLOW_REROLL_MIN + rng.nextFloat() * (OVERFLOW_REROLL_MAX - OVERFLOW_REROLL_MIN);
            outputLevel = level + 1;
        }

        byte outputType = rollOutputType(inputs, rng);

        Artifact result = ArtifactRoller.roll(outputType, outputLevel, baseValue, rng);
        result.id = UUID.randomUUID().toString();
        return new Result(result);
    }

    /**
     * Each input has a {@link #TYPE_MATCH_CHANCE} (20%) chance of determining the output type, so
     * 5 of the same type guarantees that type, and a 3/2 split yields 60%/40% odds.
     */
    private static byte rollOutputType(List<Artifact> inputs, Random rng) {
        float roll = rng.nextFloat();
        float acc = 0f;
        for (Artifact a : inputs) {
            acc += TYPE_MATCH_CHANCE;
            if (roll < acc) return a.pieceType;
        }
        return inputs.get(inputs.size() - 1).pieceType;
    }
}
