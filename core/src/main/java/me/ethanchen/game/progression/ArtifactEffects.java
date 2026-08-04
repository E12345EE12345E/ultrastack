package me.ethanchen.game.progression;

/**
 * Sums the percentage bonuses an artifact's rolled effects contribute for a given placement,
 * per the effect definitions in implementation.md, Part 3. Pure functions, easy to unit test and
 * shared by the score and meter-fill code paths.
 */
public final class ArtifactEffects {
    private ArtifactEffects() {}

    /** Piece-specific score bonus (effects a/b); only applies when the artifact's type matches the placed piece. */
    public static float scoreBonusPercent(Artifact artifact, byte pieceType, boolean lineClear, boolean spin) {
        if (artifact == null || artifact.pieceType != pieceType) return 0f;
        float total = 0f;
        for (ArtifactEffect e : artifact.effects) {
            if (e.type == ArtifactEffectType.LINE_CLEAR_SCORE && lineClear) total += e.percent();
            else if (e.type == ArtifactEffectType.SPIN_SCORE && spin) total += e.percent();
        }
        return total;
    }

    /** Piece-specific meter bonus (effects c/d), applied to every player's meter fill from this event. */
    public static float pieceMeterBonusPercent(Artifact artifact, byte pieceType, boolean lineClear, boolean spin) {
        if (artifact == null || artifact.pieceType != pieceType) return 0f;
        float total = 0f;
        for (ArtifactEffect e : artifact.effects) {
            if (e.type == ArtifactEffectType.LINE_CLEAR_METER && lineClear) total += e.percent();
            else if (e.type == ArtifactEffectType.SPIN_METER && spin) total += e.percent();
        }
        return total;
    }

    /** Non-piece-specific "while equipped" meter bonus (effects e/f), applied only to the wearer's own meter. */
    public static float equippedMeterBonusPercent(Artifact artifact, boolean lineClear, boolean spin) {
        if (artifact == null) return 0f;
        float total = 0f;
        for (ArtifactEffect e : artifact.effects) {
            if (e.type == ArtifactEffectType.EQUIPPED_LINE_CLEAR_METER && lineClear) total += e.percent();
            else if (e.type == ArtifactEffectType.EQUIPPED_SPIN_METER && spin) total += e.percent();
        }
        return total;
    }

    /** Passive time-based fill speed bonus (effect g); wired for completeness even though currently unreachable. */
    public static float equippedPassiveFillBonusPercent(Artifact artifact) {
        if (artifact == null) return 0f;
        float total = 0f;
        for (ArtifactEffect e : artifact.effects) {
            if (e.type == ArtifactEffectType.EQUIPPED_PASSIVE_FILL_SPEED) total += e.percent();
        }
        return total;
    }
}
