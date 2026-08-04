package me.ethanchen.game.progression;

/**
 * A single rolled effect entry on an {@link Artifact}. A level-N artifact holds N of these,
 * one per level, and the same {@link ArtifactEffectType} may appear multiple times at different
 * qualities (implementation.md, Part 1) -- entries are therefore always stored as a list, never
 * deduplicated or merged by type.
 */
public class ArtifactEffect {
    public ArtifactEffectType type;
    public float quality;

    /** No-arg constructor required for libGDX Json and Kryo deserialization. */
    public ArtifactEffect() {}

    public ArtifactEffect(ArtifactEffectType type, float quality) {
        this.type = type;
        this.quality = quality;
    }

    /** Exact (non-rounded) percentage bonus this entry currently provides. */
    public float percent() {
        return type.percentFor(quality);
    }

    /** Percentage bonus rounded to the nearest tenth, for display purposes only (Part 3 notes). */
    public float displayPercent() {
        return Math.round(percent() * 10f) / 10f;
    }

    /** One-line UI description, e.g. "Line clear score +12.7%". */
    public String describe() {
        return type.label() + " +" + displayPercent() + "%";
    }
}
