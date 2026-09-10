package me.ethanchen.game.progression;

import java.util.Random;

import me.ethanchen.game.board.Piece;

/** Server-authoritative Card Dealer costs and weighted artifact outcome tables. */
public final class GachaTables {
    public static final byte RARE = 0;
    public static final byte EPIC = 1;
    public static final byte LEGENDARY = 2;

    public static final long COST_1X = 100L;
    public static final long COST_10X = 1000L;

    private static final int[] RARITY_WEIGHTS = {75, 20, 5};

    public static final class Outcome {
        public final int weight;
        public final byte pieceType;
        public final int level;
        public final float baseQuality;

        public Outcome(int weight, byte pieceType, int level, float baseQuality) {
            if (weight <= 0) throw new IllegalArgumentException("weight must be positive");
            this.weight = weight;
            this.pieceType = pieceType;
            this.level = level;
            this.baseQuality = baseQuality;
        }
    }

    private static final Outcome[] RARE_TABLE = {
            new Outcome(20, Piece.Z, 1, 45f),
            new Outcome(20, Piece.S, 1, 45f),
            new Outcome(20, Piece.L, 1, 45f),
            new Outcome(20, Piece.J, 1, 45f),
            new Outcome(5, Piece.Z, 1, 75f),
            new Outcome(5, Piece.S, 1, 75f),
            new Outcome(5, Piece.L, 1, 75f),
            new Outcome(5, Piece.J, 1, 75f),
    };

    private static final Outcome[] EPIC_TABLE = {
            new Outcome(10, Piece.I, 2, 35f),
            new Outcome(10, Piece.J, 2, 35f),
            new Outcome(10, Piece.L, 2, 35f),
            new Outcome(10, Piece.O, 2, 35f),
            new Outcome(10, Piece.S, 2, 35f),
            new Outcome(10, Piece.T, 2, 35f),
            new Outcome(10, Piece.Z, 2, 35f),
            new Outcome(10, Piece.I, 2, 65f),
            new Outcome(10, Piece.O, 2, 65f),
            new Outcome(10, Piece.T, 2, 65f),
    };

    private static final Outcome[] LEGENDARY_TABLE = {
            new Outcome(50, Piece.I, 3, 95f),
            new Outcome(50, Piece.T, 3, 95f),
    };

    private GachaTables() {}

    public static long costFor(int count) {
        if (count == 1) return COST_1X;
        if (count == 10) return COST_10X;
        throw new IllegalArgumentException("dealer count must be 1 or 10");
    }

    public static byte rollRarity(Random rng) {
        return (byte) weightedIndex(RARITY_WEIGHTS, rng);
    }

    public static Outcome rollOutcome(byte rarity, Random rng) {
        Outcome[] table = tableFor(rarity);
        int total = 0;
        for (Outcome outcome : table) total = Math.addExact(total, outcome.weight);
        int roll = rng.nextInt(total);
        int accumulated = 0;
        for (Outcome outcome : table) {
            accumulated += outcome.weight;
            if (roll < accumulated) return outcome;
        }
        return table[table.length - 1];
    }

    static Outcome[] tableFor(byte rarity) {
        switch (rarity) {
            case RARE: return RARE_TABLE;
            case EPIC: return EPIC_TABLE;
            case LEGENDARY: return LEGENDARY_TABLE;
            default: throw new IllegalArgumentException("unknown card rarity " + rarity);
        }
    }

    private static int weightedIndex(int[] weights, Random rng) {
        int total = 0;
        for (int weight : weights) {
            if (weight <= 0) throw new IllegalStateException("weights must be positive");
            total = Math.addExact(total, weight);
        }
        int roll = rng.nextInt(total);
        int accumulated = 0;
        for (int i = 0; i < weights.length; i++) {
            accumulated += weights[i];
            if (roll < accumulated) return i;
        }
        return weights.length - 1;
    }
}
