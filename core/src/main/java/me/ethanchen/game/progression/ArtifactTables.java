package me.ethanchen.game.progression;

import java.util.Random;

import me.ethanchen.game.board.Piece;

/**
 * Fixed per-type effect probability tables (implementation.md, Part 3). Each row grants one or
 * more effect letters simultaneously at a shared base quality {@code q}. The listed percentages
 * for a type's rows do not sum to 100 in the design doc; they are treated here as relative weights
 * within that type's table and normalized when picking a single row per artifact level, since the
 * design requires every level to grant at least one effect entry.
 */
public final class ArtifactTables {

    /** One row of a type's effect table: relative weight, the effect letters granted together, and their shared base quality. */
    public static final class Row {
        public final float weight;
        public final ArtifactEffectType[] effects;
        public final float quality;

        public Row(float weight, float quality, ArtifactEffectType... effects) {
            this.weight = weight;
            this.quality = quality;
            this.effects = effects;
        }
    }

    private static final ArtifactEffectType A = ArtifactEffectType.LINE_CLEAR_SCORE;
    private static final ArtifactEffectType B = ArtifactEffectType.SPIN_SCORE;
    private static final ArtifactEffectType C = ArtifactEffectType.LINE_CLEAR_METER;
    private static final ArtifactEffectType D = ArtifactEffectType.SPIN_METER;
    private static final ArtifactEffectType E = ArtifactEffectType.EQUIPPED_LINE_CLEAR_METER;
    private static final ArtifactEffectType F = ArtifactEffectType.EQUIPPED_SPIN_METER;

    private static final Row[] I_TABLE = {
            new Row(20f, 8f, A, C),
            new Row(10f, 15f, B, D),
            new Row(20f, 10f, E, F),
    };

    private static final Row[] T_TABLE = {
            new Row(15f, 8f, A, B, C, D),
            new Row(20f, 10f, E, F),
    };

    private static final Row[] O_TABLE = {
            new Row(45f, 18f, A, C),
            new Row(5f, 20f, E, F),
    };

    private static final Row[] SZLJ_TABLE = {
            new Row(10f, 14f, A, C),
            new Row(20f, 16f, B, D),
            new Row(20f, 10f, E, F),
    };

    private ArtifactTables() {}

    /** Returns the effect table for a given tetromino piece type (only I, T, O, S, Z, L, J are valid). */
    public static Row[] tableFor(byte pieceType) {
        switch (pieceType) {
            case Piece.I: return I_TABLE;
            case Piece.T: return T_TABLE;
            case Piece.O: return O_TABLE;
            case Piece.S: case Piece.Z: case Piece.L: case Piece.J: return SZLJ_TABLE;
            default:
                throw new IllegalArgumentException("No artifact table for piece type " + pieceType);
        }
    }

    /** Picks a single weighted row from the given piece type's table. */
    public static Row rollRow(byte pieceType, Random rng) {
        Row[] table = tableFor(pieceType);
        float total = 0f;
        for (Row r : table) total += r.weight;
        float roll = rng.nextFloat() * total;
        float acc = 0f;
        for (Row r : table) {
            acc += r.weight;
            if (roll < acc) return r;
        }
        return table[table.length - 1];
    }
}
