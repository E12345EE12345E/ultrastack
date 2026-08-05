package me.ethanchen.game.progression;

import java.util.Random;

import me.ethanchen.game.board.Piece;

/**
 * Fixed per-type effect probability tables (implementation.md, Part 3). Each entry is one
 * mutually exclusive outcome with an absolute percentage; the percentages in a type's table
 * always sum to 100, so each level roll picks exactly one effect letter. Grouped notation such
 * as {@code 20% a,c [q=8]} expands to a 20% chance of {@code a} and a 20% chance of {@code c}
 * (both with {@code q=8}) as separate outcomes in that exclusive pick.
 */
public final class ArtifactTables {

    /** One mutually exclusive outcome: absolute percent chance to grant {@code effect} at base {@code quality}. */
    public static final class Chance {
        /** Absolute percentage; all chances in a table sum to 100. */
        public final float percent;
        public final ArtifactEffectType effect;
        public final float quality;

        public Chance(float percent, float quality, ArtifactEffectType effect) {
            this.percent = percent;
            this.quality = quality;
            this.effect = effect;
        }
    }

    private static final ArtifactEffectType A = ArtifactEffectType.LINE_CLEAR_SCORE;
    private static final ArtifactEffectType B = ArtifactEffectType.SPIN_SCORE;
    private static final ArtifactEffectType C = ArtifactEffectType.LINE_CLEAR_METER;
    private static final ArtifactEffectType D = ArtifactEffectType.SPIN_METER;
    private static final ArtifactEffectType E = ArtifactEffectType.EQUIPPED_LINE_CLEAR_METER;
    private static final ArtifactEffectType F = ArtifactEffectType.EQUIPPED_SPIN_METER;
    private static final ArtifactEffectType G = ArtifactEffectType.EQUIPPED_LINE_CLEAR_SCORE;
    private static final ArtifactEffectType H = ArtifactEffectType.EQUIPPED_SPIN_SCORE;

    private static final Chance[] I_TABLE = {
            new Chance(20f, 20f, A), new Chance(20f, 20f, C),
            new Chance(10f, 60f, B), new Chance(10f, 60f, D),
            new Chance(10f, 30f, E), new Chance(10f, 30f, F),
            new Chance(10f, 30f, G), new Chance(10f, 30f, H),
    };

    private static final Chance[] T_TABLE = {
            new Chance(15f, 20f, A), new Chance(15f, 20f, B),
            new Chance(15f, 20f, C), new Chance(15f, 20f, D),
            new Chance(10f, 30f, E), new Chance(10f, 30f, F),
            new Chance(10f, 30f, G), new Chance(10f, 30f, H),
    };

    private static final Chance[] O_TABLE = {
            new Chance(40f, 100f, A), new Chance(40f, 100f, C),
            new Chance(5f, 75f, E), new Chance(5f, 75f, F),
            new Chance(5f, 75f, G), new Chance(5f, 75f, H),
    };

    private static final Chance[] SZLJ_TABLE = {
            new Chance(10f, 60f, A), new Chance(10f, 60f, C),
            new Chance(20f, 80f, B), new Chance(20f, 80f, D),
            new Chance(10f, 30f, E), new Chance(10f, 30f, F),
            new Chance(10f, 30f, G), new Chance(10f, 30f, H),
    };

    private ArtifactTables() {}

    /** Returns the exclusive-outcome table for a given tetromino piece type. */
    public static Chance[] tableFor(byte pieceType) {
        switch (pieceType) {
            case Piece.I: return I_TABLE;
            case Piece.T: return T_TABLE;
            case Piece.O: return O_TABLE;
            case Piece.S: case Piece.Z: case Piece.L: case Piece.J: return SZLJ_TABLE;
            default:
                throw new IllegalArgumentException("No artifact table for piece type " + pieceType);
        }
    }

    /** Picks exactly one chance from the piece type's table (percentages sum to 100). */
    public static Chance rollChance(byte pieceType, Random rng) {
        Chance[] table = tableFor(pieceType);
        float roll = rng.nextFloat() * 100f;
        float acc = 0f;
        for (Chance c : table) {
            acc += c.percent;
            if (roll < acc) return c;
        }
        return table[table.length - 1];
    }
}
