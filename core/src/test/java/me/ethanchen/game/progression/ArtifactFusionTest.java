package me.ethanchen.game.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.board.Piece;

class ArtifactFusionTest {

    @Test
    void validateRequiresFiveSameLevel() {
        assertEquals("Select exactly 5 artifacts to fuse.", ArtifactFusion.validate(null));
        assertEquals("Select exactly 5 artifacts to fuse.", ArtifactFusion.validate(List.of(a(1, 50))));
        assertEquals("Incompatible artifact levels", ArtifactFusion.validate(List.of(
                a(1, 50), a(1, 50), a(1, 50), a(1, 50), a(2, 50))));
        assertNull(ArtifactFusion.validate(List.of(
                a(1, 10), a(1, 90), a(1, 40), a(1, 40), a(1, 40))));
    }

    @Test
    void fuseThrowsOnInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> ArtifactFusion.fuse(List.of(), new Random(0)));
    }

    @Test
    void overflowPromotesLevel() {
        // 90 + (40+40+40+40)/4 + 0 = 130 > 100 → level 2
        ArtifactFusion.Result r = ArtifactFusion.fuse(List.of(
                a(1, 90), a(1, 40), a(1, 40), a(1, 40), a(1, 40)), zeroFloatRng());
        assertEquals(2, r.artifact.level);
    }

    @Test
    void noOverflowKeepsLevel() {
        // 50 + (10*4)/4 + 0 = 60 → stay level 1
        ArtifactFusion.Result r = ArtifactFusion.fuse(List.of(
                a(1, 50), a(1, 10), a(1, 10), a(1, 10), a(1, 10)), zeroFloatRng());
        assertEquals(1, r.artifact.level);
    }

    private static Artifact a(int level, float quality) {
        return new Artifact("id-" + level + "-" + quality, Piece.I, level, quality);
    }

    /** nextFloat() == 0 so the 0–20 bonus is zero and type inheritance picks the first input. */
    private static Random zeroFloatRng() {
        return new Random(0) {
            @Override
            public float nextFloat() {
                return 0f;
            }
        };
    }
}
