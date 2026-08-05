package me.ethanchen.game.progression;

/**
 * Gameplay effects an artifact can roll, lettered per the design outline (implementation.md, Part 3).
 * Each effect stores the percent-per-quality-point coefficient used to turn a rolled {@code quality}
 * into an actual percentage bonus, plus whether the effect only applies while using that artifact's
 * own piece type.
 */
public enum ArtifactEffectType {
    /** All line clears with this piece score {@code 1.0*q} percent more. */
    LINE_CLEAR_SCORE(1.0f, true),
    /** All spins with this piece score {@code 2.0*q} percent more. */
    SPIN_SCORE(2.0f, true),
    /** All line clears with this piece fill players' meters {@code 1.5*q} percent more. */
    LINE_CLEAR_METER(1.5f, true),
    /** All spins with this piece fill players' meters {@code 3.0*q} percent more. */
    SPIN_METER(3.0f, true),
    /** While equipped, any line clear fills your meter {@code 0.25*q} percent more. */
    EQUIPPED_LINE_CLEAR_METER(0.25f, false),
    /** While equipped, any spin fills your meter {@code 0.5*q} percent more. */
    EQUIPPED_SPIN_METER(0.5f, false),
    /** Currently unobtainable: while equipped, passive time-based meter fill speed is increased by {@code 2.0*q} percent. */
    EQUIPPED_PASSIVE_FILL_SPEED(2.0f, false, false),
    /** While equipped, any line clear scores {@code 0.1*q} percent more. */
    EQUIPPED_LINE_CLEAR_SCORE(0.1f, false),
    /** While equipped, any spin scores {@code 0.2*q} percent more. */
    EQUIPPED_SPIN_SCORE(0.2f, false);

    private final float coefficient;
    private final boolean pieceSpecific;
    private final boolean obtainable;

    ArtifactEffectType(float coefficient, boolean pieceSpecific) {
        this(coefficient, pieceSpecific, true);
    }

    ArtifactEffectType(float coefficient, boolean pieceSpecific, boolean obtainable) {
        this.coefficient = coefficient;
        this.pieceSpecific = pieceSpecific;
        this.obtainable = obtainable;
    }

    /** Percent bonus per unit of quality, e.g. {@code 2.0} for {@link #SPIN_SCORE}. */
    public float coefficient() { return coefficient; }

    /** True for effects a-d, which only apply when scoring/filling with this artifact's own piece type. */
    public boolean isPieceSpecific() { return pieceSpecific; }

    /** False for {@link #EQUIPPED_PASSIVE_FILL_SPEED}, which is defined but not currently reachable through any roll table. */
    public boolean isObtainable() { return obtainable; }

    /** Returns the exact (non-rounded) percentage bonus for a given quality value. */
    public float percentFor(float quality) {
        return coefficient * quality;
    }

    /** Short human-readable label for UI display. */
    public String label() {
        switch (this) {
            case LINE_CLEAR_SCORE:            return "line clears score";
            case SPIN_SCORE:                   return "spins score";
            case LINE_CLEAR_METER:             return "line clear meter";
            case SPIN_METER:                   return "spin meter";
            case EQUIPPED_LINE_CLEAR_METER:    return "All line clears meter";
            case EQUIPPED_SPIN_METER:          return "Any spins meter";
            case EQUIPPED_PASSIVE_FILL_SPEED:  return "Meter fill over time";
            case EQUIPPED_LINE_CLEAR_SCORE:    return "All line clears score";
            case EQUIPPED_SPIN_SCORE:          return "Any spins score";
            default: return name();
        }
    }
}
