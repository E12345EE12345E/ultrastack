package me.ethanchen.game.progression;

import java.util.Random;

import me.ethanchen.game.board.Piece;

/**
 * Builds the session-only profile handed to LAN players: every character unlocked and two
 * pre-generated artifacts to experiment with, but never persisted and never eligible for further
 * acquisition or fusion (implementation.md, Part 5).
 */
public final class LanProfileFactory {
    private static final byte[] TETROMINOES = {
            Piece.I, Piece.J, Piece.L, Piece.O, Piece.S, Piece.T, Piece.Z
    };

    private LanProfileFactory() {}

    public static PlayerProfile create() {
        PlayerProfile profile = new PlayerProfile();
        for (int i = 0; i < CharacterRegistry.count(); i++) {
            profile.unlockCharacter(i);
        }
        profile.selectedCharacterId = 0;

        Random rng = new Random();
        Artifact a = ArtifactRoller.roll(randomTetromino(rng), 1, 60f, rng);
        Artifact b = ArtifactRoller.roll(randomTetromino(rng), 1, 60f, rng);
        profile.inventory.add(a);
        profile.inventory.add(b);
        profile.equippedArtifactIds[0] = a.id;
        profile.equippedArtifactIds[1] = b.id;
        return profile;
    }

    private static byte randomTetromino(Random rng) {
        return TETROMINOES[rng.nextInt(TETROMINOES.length)];
    }
}
