package me.ethanchen.game.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class ArtifactAcquisitionTest {

    @Test
    void higherXpNeverLowersBaseQuality() {
        assertEquals(0f, ArtifactAcquisition.baseQualityFromXp(0));
        assertEquals(50f, ArtifactAcquisition.baseQualityFromXp(50));
        assertEquals(100f, ArtifactAcquisition.baseQualityFromXp(100));
        assertEquals(100f, ArtifactAcquisition.baseQualityFromXp(9999));
    }

    @Test
    void rolledArtifactIsLevelOneTetromino() {
        Random rng = new Random(7);
        Artifact a = ArtifactAcquisition.rollFromVictory(42, rng);
        assertEquals(1, a.level);
        assertTrue(Artifact.isTetrominoType(a.pieceType));
        assertTrue(a.effects.size() >= 1, "level 1 should roll at least one effect entry");
    }
}
