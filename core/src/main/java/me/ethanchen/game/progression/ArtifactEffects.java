package me.ethanchen.game.progression;

/**
 * Sums the percentage bonuses an artifact contributes for a given placement: rolled
 * {@link ArtifactEffect}s plus the type's built-in {@link ArtifactUniqueEffect}. Pure functions
 * shared by the score and meter-fill code paths.
 */
public final class ArtifactEffects {
    private ArtifactEffects() {}

    /** Piece-specific score bonus (effects a/b + matching unique); only when artifact type matches the placed piece. */
    public static float scoreBonusPercent(Artifact artifact, byte pieceType, boolean lineClear, boolean spin) {
        if (artifact == null || artifact.pieceType != pieceType) return 0f;
        float total = 0f;
        for (ArtifactEffect e : artifact.effects) {
            if (e.type == ArtifactEffectType.LINE_CLEAR_SCORE && lineClear) total += e.percent();
            else if (e.type == ArtifactEffectType.SPIN_SCORE && spin) total += e.percent();
        }
        if (lineClear) total += ArtifactUniqueEffects.percentIf(artifact, ArtifactEffectType.LINE_CLEAR_SCORE);
        if (spin) total += ArtifactUniqueEffects.percentIf(artifact, ArtifactEffectType.SPIN_SCORE);
        return total;
    }

    /** Piece-specific meter bonus (effects c/d + matching unique), applied to every player's meter fill. */
    public static float pieceMeterBonusPercent(Artifact artifact, byte pieceType, boolean lineClear, boolean spin) {
        if (artifact == null || artifact.pieceType != pieceType) return 0f;
        float total = 0f;
        for (ArtifactEffect e : artifact.effects) {
            if (e.type == ArtifactEffectType.LINE_CLEAR_METER && lineClear) total += e.percent();
            else if (e.type == ArtifactEffectType.SPIN_METER && spin) total += e.percent();
        }
        if (lineClear) total += ArtifactUniqueEffects.percentIf(artifact, ArtifactEffectType.LINE_CLEAR_METER);
        if (spin) total += ArtifactUniqueEffects.percentIf(artifact, ArtifactEffectType.SPIN_METER);
        return total;
    }

    /** Non-piece-specific "while equipped" meter bonus (effects e/f + matching unique), wearer's meter only. */
    public static float equippedMeterBonusPercent(Artifact artifact, boolean lineClear, boolean spin) {
        if (artifact == null) return 0f;
        float total = 0f;
        for (ArtifactEffect e : artifact.effects) {
            if (e.type == ArtifactEffectType.EQUIPPED_LINE_CLEAR_METER && lineClear) total += e.percent();
            else if (e.type == ArtifactEffectType.EQUIPPED_SPIN_METER && spin) total += e.percent();
        }
        if (lineClear) total += ArtifactUniqueEffects.percentIf(artifact, ArtifactEffectType.EQUIPPED_LINE_CLEAR_METER);
        if (spin) total += ArtifactUniqueEffects.percentIf(artifact, ArtifactEffectType.EQUIPPED_SPIN_METER);
        return total;
    }

    /** Non-piece-specific "while equipped" score bonus (rolled + matching unique), wearer's score on any piece. */
    public static float equippedScoreBonusPercent(Artifact artifact, boolean lineClear, boolean spin) {
        if (artifact == null) return 0f;
        float total = 0f;
        for (ArtifactEffect e : artifact.effects) {
            if (e.type == ArtifactEffectType.EQUIPPED_LINE_CLEAR_SCORE && lineClear) total += e.percent();
            else if (e.type == ArtifactEffectType.EQUIPPED_SPIN_SCORE && spin) total += e.percent();
        }
        if (lineClear) total += ArtifactUniqueEffects.percentIf(artifact, ArtifactEffectType.EQUIPPED_LINE_CLEAR_SCORE);
        if (spin) total += ArtifactUniqueEffects.percentIf(artifact, ArtifactEffectType.EQUIPPED_SPIN_SCORE);
        return total;
    }

    /** Passive time-based fill speed bonus (effect g + matching unique). */
    public static float equippedPassiveFillBonusPercent(Artifact artifact) {
        if (artifact == null) return 0f;
        float total = 0f;
        for (ArtifactEffect e : artifact.effects) {
            if (e.type == ArtifactEffectType.EQUIPPED_PASSIVE_FILL_SPEED) total += e.percent();
        }
        total += ArtifactUniqueEffects.percentIf(artifact, ArtifactEffectType.EQUIPPED_PASSIVE_FILL_SPEED);
        return total;
    }
}
