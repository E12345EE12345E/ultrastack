package me.ethanchen.game.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class ArtifactAcquisitionTest {

    @Test
    void rolledArtifactIsLevelOneTetromino() {
        Random rng = new Random(7);
        Artifact a = ArtifactAcquisition.rollFromVictory(42, rng);
        assertEquals(1, a.level);
        assertTrue(Artifact.isTetrominoType(a.pieceType));
        assertEquals(1, a.effects.size());
    }
}
