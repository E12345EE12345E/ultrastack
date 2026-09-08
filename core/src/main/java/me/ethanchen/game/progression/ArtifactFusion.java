package me.ethanchen.game.progression;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Implements artifact fusion: five same-level artifacts are consumed to produce one new artifact
 * of the same level (or one higher on a quality overflow).
 */
public final class ArtifactFusion {

    private static final int FUSION_INPUT_COUNT = 5;
    private static final float OVERFLOW_THRESHOLD = 100f;
    private static final float BONUS_QUALITY_MAX = 20f;
    private static final float OVERFLOW_BASE = 20f;
    private static final float TYPE_MATCH_CHANCE = 0.2f;

    private ArtifactFusion() {}

    public static final class Result {
        public final Artifact artifact;
        public Result(Artifact artifact) { this.artifact = artifact; }
    }

    /**
     * {@code null} when the five inputs can fuse, else a human-readable reason the client can
     * show in place of the Fusion button.
     */
    public static String validate(List<Artifact> inputs) {
        if (inputs == null || inputs.size() != FUSION_INPUT_COUNT) {
            return "Select exactly 5 artifacts to fuse.";
        }
        for (Artifact a : inputs) {
            if (a == null) return "Select exactly 5 artifacts to fuse.";
        }
        int level = inputs.get(0).level;
        for (Artifact a : inputs) {
            if (a.level != level) return "Incompatible artifact levels";
        }
        return null;
    }

    /**
     * Fuses exactly five same-level artifacts. Throws {@link IllegalArgumentException} if the
     * precondition (count == 5, all same level) is violated -- callers should validate ownership
     * and non-equipped status separately before calling this.
     */
    public static Result fuse(List<Artifact> inputs, Random rng) {
        String reason = validate(inputs);
        if (reason != null) {
            throw new IllegalArgumentException(reason);
        }
        int level = inputs.get(0).level;

        int bestIndex = 0;
        for (int i = 1; i < FUSION_INPUT_COUNT; i++) {
            if (inputs.get(i).baseQuality > inputs.get(bestIndex).baseQuality) bestIndex = i;
        }
        float best = inputs.get(bestIndex).baseQuality;
        float otherSum = 0f;
        for (int i = 0; i < FUSION_INPUT_COUNT; i++) {
            if (i == bestIndex) continue;
            otherSum += inputs.get(i).baseQuality;
        }
        float quality = best + otherSum / 4f + rng.nextFloat() * BONUS_QUALITY_MAX;

        int outputLevel = level;
        float baseValue = quality;
        if (quality > OVERFLOW_THRESHOLD) {
            outputLevel = level + 1;
            baseValue = Math.min(quality - OVERFLOW_THRESHOLD, BONUS_QUALITY_MAX) + OVERFLOW_BASE;
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
