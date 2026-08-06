package me.ethanchen.game.progression;

import java.util.ArrayList;
import java.util.List;

import me.ethanchen.game.board.Piece;

/**
 * A single acquired artifact instance: a piece-typed item with a hidden {@code baseQuality} that
 * biases future effect rolls, a list of independently-rolled effect entries -- exactly one per
 * level -- and a built-in {@linkplain #uniqueEffect() unique effect} derived from type and level
 * only (never stored; implementation.md, Parts 1 and 3).
 */
public class Artifact {
    public String id;
    public byte pieceType;
    public int level;
    public float baseQuality;
    public List<ArtifactEffect> effects;

    /** No-arg constructor required for libGDX Json and Kryo deserialization. */
    public Artifact() {
        this.effects = new ArrayList<>();
    }

    public Artifact(String id, byte pieceType, int level, float baseQuality) {
        this.id = id;
        this.pieceType = pieceType;
        this.level = level;
        this.baseQuality = baseQuality;
        this.effects = new ArrayList<>();
    }

    public static boolean isTetrominoType(byte pieceType) {
        switch (pieceType) {
            case Piece.I: case Piece.J: case Piece.L: case Piece.O:
            case Piece.S: case Piece.T: case Piece.Z:
                return true;
            default:
                return false;
        }
    }

    /** Short display name for {@link #pieceType}, e.g. "I", "L3". */
    public static String pieceTypeName(byte pieceType) {
        switch (pieceType) {
            case Piece.I: return "I";
            case Piece.J: return "J";
            case Piece.L: return "L";
            case Piece.O: return "O";
            case Piece.S: return "S";
            case Piece.T: return "T";
            case Piece.Z: return "Z";
            case Piece.I3: return "I3";
            case Piece.L3: return "L3";
            default: return "?";
        }
    }

    /** UI display name, e.g. "I Artifact (Lv2)". */
    public String displayName() {
        return pieceTypeName(pieceType) + " Artifact (Lv" + level + ")";
    }

    /**
     * Bottom-right inventory badge count: one star per level, capped at 5
     * (higher levels still show five stars for now).
     */
    public int levelStarCount() {
        return Math.max(0, Math.min(5, level));
    }

    /**
     * Bottom-left inventory badge: {@link #baseQuality} rounded to the nearest int and clamped
     * to {@code [0, 99]}.
     */
    public String qualityLabel() {
        int q = Math.round(baseQuality);
        if (q < 0) q = 0;
        if (q > 99) q = 99;
        return Integer.toString(q);
    }

    /**
     * Inventory sort: higher {@link #level} first, then higher {@link #baseQuality}
     * within the same level. Stable for equal level/quality (returns 0).
     */
    public static int compareForInventory(Artifact a, Artifact b) {
        int levelCmp = Integer.compare(b.level, a.level);
        if (levelCmp != 0) return levelCmp;
        return Float.compare(b.baseQuality, a.baseQuality);
    }

    /**
     * Built-in unique effect for this artifact's type, derived from {@link #pieceType} and
     * {@link #level} only -- never stored on the instance or in account JSON.
     */
    public ArtifactUniqueEffect uniqueEffect() {
        return ArtifactUniqueEffects.forPieceType(pieceType);
    }

    /**
     * UI stats block: display name, unique effect (if any), then up to {@code maxEffects}
     * remaining rolled-effect lines. Effect lines use libGDX color markup.
     */
    public String describeForUi(int maxEffects) {
        StringBuilder sb = new StringBuilder(displayName());
        int remaining = Math.max(0, maxEffects);
        ArtifactUniqueEffect unique = uniqueEffect();
        if (unique != null && remaining > 0) {
            sb.append('\n').append(unique.describe(pieceType, level));
            remaining--;
        }
        int n = Math.min(remaining, effects.size());
        for (int i = 0; i < n; i++) {
            sb.append('\n').append(effects.get(i).describe(pieceType));
        }
        return sb.toString();
    }
}
