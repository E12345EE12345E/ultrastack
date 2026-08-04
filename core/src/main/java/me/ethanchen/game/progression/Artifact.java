package me.ethanchen.game.progression;

import java.util.ArrayList;
import java.util.List;

import me.ethanchen.game.board.Piece;

/**
 * A single acquired artifact instance: a piece-typed item with a hidden {@code baseQuality} that
 * biases future effect rolls, and a list of independently-rolled effect entries -- exactly one
 * per level (implementation.md, Parts 1 and 3).
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
}
