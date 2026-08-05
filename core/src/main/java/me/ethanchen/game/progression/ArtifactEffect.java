package me.ethanchen.game.progression;

/**
 * A single rolled effect entry on an {@link Artifact}. A level-N artifact holds N of these,
 * one per level, and the same {@link ArtifactEffectType} may appear multiple times at different
 * qualities (implementation.md, Part 1) -- entries are therefore always stored as a list, never
 * deduplicated or merged by type.
 */
public class ArtifactEffect {
    /** libGDX BitmapFont markup gold for piece letters. */
    private static final String COLOR_PIECE = "FFD700";
    /** Percent tiers: 0–100 yellow, 100-200 lime, 200+ cyan. */
    private static final String COLOR_PCT_LOW = "FFFF00";
    private static final String COLOR_PCT_MID = "32CD32";
    private static final String COLOR_PCT_HIGH = "00FFFF";

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

    /**
     * One-line UI description with libGDX color markup: gold piece letter (when piece-specific)
     * and tiered percent color (yellow / lime / cyan).
     */
    public String describe(byte pieceType) {
        String pct = percentMarkup(displayPercent());
        switch (type) {
            case LINE_CLEAR_SCORE:
                return pieceMarkup(pieceType) + "-piece line clears score " + pct;
            case SPIN_SCORE:
                return pieceMarkup(pieceType) + "-piece spins score " + pct;
            case LINE_CLEAR_METER:
                return pieceMarkup(pieceType) + "-piece line clear meter bonus " + pct;
            case SPIN_METER:
                return pieceMarkup(pieceType) + "-piece spin meter bonus " + pct;
            case EQUIPPED_LINE_CLEAR_METER:
                return "All line clears meter bonus " + pct;
            case EQUIPPED_SPIN_METER:
                return "All spins meter bonus " + pct;
            case EQUIPPED_PASSIVE_FILL_SPEED:
                return "Meter fill over time " + pct;
            case EQUIPPED_LINE_CLEAR_SCORE:
                return "All line clears score " + pct;
            case EQUIPPED_SPIN_SCORE:
                return "All spins score " + pct;
            default:
                return type.name() + " " + pct;
        }
    }

    private static String pieceMarkup(byte pieceType) {
        return "[#" + COLOR_PIECE + "]" + Artifact.pieceTypeName(pieceType) + "[]";
    }

    private static String percentMarkup(float displayPercent) {
        String hex;
        if (displayPercent < 100f) hex = COLOR_PCT_LOW;
        else if (displayPercent < 200f) hex = COLOR_PCT_MID;
        else hex = COLOR_PCT_HIGH;
        String body = displayPercent == Math.rint(displayPercent)
                ? String.format("+%.0f%%", displayPercent)
                : String.format("+%.1f%%", displayPercent);
        return "[#" + hex + "]" + body + "[]";
    }
}
