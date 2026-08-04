package me.ethanchen.game.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.board.Piece;

class ArtifactFusionTest {

    private Artifact artifactOf(byte type, int level, float baseQuality) {
        Artifact a = ArtifactRoller.roll(type, level, baseQuality, new Random(0));
        a.baseQuality = baseQuality;
        return a;
    }

    @Test
    void rejectsWrongInputCount() {
        List<Artifact> four = new ArrayList<>();
        for (int i = 0; i < 4; i++) four.add(artifactOf(Piece.I, 1, 50f));
        assertThrows(IllegalArgumentException.class, () -> ArtifactFusion.fuse(four, new Random(1)));
    }

    @Test
    void rejectsMixedLevels() {
        List<Artifact> mixed = new ArrayList<>();
        mixed.add(artifactOf(Piece.I, 1, 50f));
        mixed.add(artifactOf(Piece.I, 2, 50f));
        for (int i = 0; i < 3; i++) mixed.add(artifactOf(Piece.I, 1, 50f));
        assertThrows(IllegalArgumentException.class, () -> ArtifactFusion.fuse(mixed, new Random(1)));
    }

    @Test
    void sameTypeInputsGuaranteeSameTypeOutput() {
        List<Artifact> all = new ArrayList<>();
        for (int i = 0; i < 5; i++) all.add(artifactOf(Piece.Z, 1, 30f));
        for (int seed = 0; seed < 20; seed++) {
            ArtifactFusion.Result r = ArtifactFusion.fuse(all, new Random(seed));
            assertEquals(Piece.Z, r.artifact.pieceType);
        }
    }

    @Test
    void overflowingBaseQualityLevelsUpAndRerollsWithinRange() {
        // All base 100 with all x2.0 multipliers guarantees overflow (200 > 100).
        List<Artifact> highQuality = new ArrayList<>();
        for (int i = 0; i < 5; i++) highQuality.add(artifactOf(Piece.T, 2, 100f));
        // Random seeded such that nextBoolean() always returns true (x2.0) is not guaranteed by seed,
        // so instead assert the invariant holds whenever overflow happens across many seeds.
        boolean sawOverflow = false;
        for (int seed = 0; seed < 200; seed++) {
            List<Artifact> copy = new ArrayList<>();
            for (Artifact a : highQuality) copy.add(cloneWithBase(a, 100f));
            ArtifactFusion.Result r = ArtifactFusion.fuse(copy, new Random(seed));
            if (r.artifact.level == 3) {
                sawOverflow = true;
                assertTrue(r.artifact.baseQuality >= 40f && r.artifact.baseQuality <= 80f);
            } else {
                assertEquals(2, r.artifact.level);
            }
        }
        assertTrue(sawOverflow, "expected at least one overflow with all base-100 inputs across 200 seeds");
    }

    private Artifact cloneWithBase(Artifact a, float baseQuality) {
        Artifact copy = new Artifact(a.id, a.pieceType, a.level, baseQuality);
        copy.effects.addAll(a.effects);
        return copy;
    }
}
