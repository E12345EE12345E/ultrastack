package me.ethanchen.game.progression;

/**
 * Built-in effect granted by an artifact's type. Scales with {@link Artifact#level} only
 * (ignores {@link Artifact#baseQuality}), is never randomized, and is never persisted --
 * callers derive it from piece type + level at runtime.
 */
public final class ArtifactUniqueEffect {
    /** Same gameplay bucket as a rolled {@link ArtifactEffect}; used for score/meter application. */
    public final ArtifactEffectType type;
    /** Percentage bonus per artifact level, e.g. {@code 20} for {@code +[level*20]%}. */
    public final float percentPerLevel;

    public ArtifactUniqueEffect(ArtifactEffectType type, float percentPerLevel) {
        this.type = type;
        this.percentPerLevel = percentPerLevel;
    }

    /** Exact (non-rounded) percentage at the given artifact level. */
    public float percent(int level) {
        return percentPerLevel * Math.max(0, level);
    }

    /** Percentage rounded to the nearest tenth, for display only. */
    public float displayPercent(int level) {
        return Math.round(percent(level) * 10f) / 10f;
    }

    /**
     * One-line UI description with the same libGDX color markup as rolled effects, wrapped in
     * {@code [ ]}. Opening bracket is escaped as {@code [[} for BitmapFont markup.
     */
    public String describe(byte pieceType, int level) {
        return "[[ " + ArtifactEffect.describe(type, pieceType, displayPercent(level)) + " ]";
    }
}
