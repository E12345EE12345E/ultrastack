package me.ethanchen.game.progression;

import java.util.Random;

import me.ethanchen.game.board.Piece;

/**
 * Rolls the artifact granted for a game victory (implementation.md, Part 2): a fixed level-1
 * artifact of a random tetromino type, with a hidden base quality derived from the xp earned
 * ("more xp means a higher base quality", bounded 0-100).
 */
public final class ArtifactAcquisition {
    private static final byte[] TETROMINOES = {
            Piece.I, Piece.J, Piece.L, Piece.O, Piece.S, Piece.T, Piece.Z
    };

    private ArtifactAcquisition() {}

    /** Maps earned xp directly to a 0-100 base quality; xp above 100 simply caps at max quality. */
    public static float baseQualityFromXp(long xp) {
        return Math.max(0f, Math.min(100f, (float) xp));
    }

    public static Artifact rollFromVictory(long xp, Random rng) {
        byte type = TETROMINOES[rng.nextInt(TETROMINOES.length)];
        float baseQuality = baseQualityFromXp(xp);
        return ArtifactRoller.roll(type, 1, baseQuality, rng);
    }
}
