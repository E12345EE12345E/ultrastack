package me.ethanchen.game.progression;

import me.ethanchen.game.board.Piece;

/**
 * Per-artifact-type unique effect definitions. Each tetromino type (and future non-tetromino
 * types) declares its own built-in effect here; nothing is stored on {@link Artifact} or in
 * account JSON.
 */
public final class ArtifactUniqueEffects {

    /** I: I-piece line clears fill meter {@code +[level*20]%}. */
    public static final ArtifactUniqueEffect I =
            new ArtifactUniqueEffect(ArtifactEffectType.LINE_CLEAR_METER, 20f);

    /** J: J-piece spins score {@code +[level*20]%}. */
    public static final ArtifactUniqueEffect J =
            new ArtifactUniqueEffect(ArtifactEffectType.SPIN_SCORE, 20f);

    /** L: L-piece spins score {@code +[level*20]%}. */
    public static final ArtifactUniqueEffect L =
            new ArtifactUniqueEffect(ArtifactEffectType.SPIN_SCORE, 20f);

    /** O: all line clears score {@code +[level*1.5]%}. */
    public static final ArtifactUniqueEffect O =
            new ArtifactUniqueEffect(ArtifactEffectType.EQUIPPED_LINE_CLEAR_SCORE, 1.5f);

    /** S: S-piece spins score {@code +[level*20]%}. */
    public static final ArtifactUniqueEffect S =
            new ArtifactUniqueEffect(ArtifactEffectType.SPIN_SCORE, 20f);

    /** Z: Z-piece spins score {@code +[level*20]%}. */
    public static final ArtifactUniqueEffect Z =
            new ArtifactUniqueEffect(ArtifactEffectType.SPIN_SCORE, 20f);

    /** T: T-piece spins fill meter {@code +[level*25]%}. */
    public static final ArtifactUniqueEffect T =
            new ArtifactUniqueEffect(ArtifactEffectType.SPIN_METER, 25f);

    private ArtifactUniqueEffects() {}

    /** Returns the unique effect for {@code pieceType}, or {@code null} if none is defined. */
    public static ArtifactUniqueEffect forPieceType(byte pieceType) {
        switch (pieceType) {
            case Piece.I: return I;
            case Piece.J: return J;
            case Piece.L: return L;
            case Piece.O: return O;
            case Piece.S: return S;
            case Piece.Z: return Z;
            case Piece.T: return T;
            default: return null;
        }
    }

    /** Resolves {@code artifact}'s unique effect from its type, or {@code null}. */
    public static ArtifactUniqueEffect of(Artifact artifact) {
        return artifact == null ? null : forPieceType(artifact.pieceType);
    }

    /**
     * Percentage contributed by {@code artifact}'s unique effect when its type matches
     * {@code expected}, else {@code 0}.
     */
    public static float percentIf(Artifact artifact, ArtifactEffectType expected) {
        ArtifactUniqueEffect unique = of(artifact);
        if (unique == null || unique.type != expected) return 0f;
        return unique.percent(artifact.level);
    }
}
