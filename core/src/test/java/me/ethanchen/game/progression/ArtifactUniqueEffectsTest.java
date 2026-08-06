package me.ethanchen.game.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.board.Piece;

class ArtifactUniqueEffectsTest {

    @Test
    void iUnique_scalesLineClearMeterWithLevel() {
        Artifact a = bare(Piece.I, 3);
        assertEquals(60f, ArtifactEffects.pieceMeterBonusPercent(a, Piece.I, true, false));
        assertEquals(0f, ArtifactEffects.pieceMeterBonusPercent(a, Piece.I, false, true));
        assertEquals(0f, ArtifactEffects.pieceMeterBonusPercent(a, Piece.T, true, false));
    }

    @Test
    void jlUnique_scalesSpinScoreWithLevel() {
        Artifact j = bare(Piece.J, 2);
        Artifact l = bare(Piece.L, 1);
        assertEquals(40f, ArtifactEffects.scoreBonusPercent(j, Piece.J, false, true));
        assertEquals(20f, ArtifactEffects.scoreBonusPercent(l, Piece.L, false, true));
        assertEquals(0f, ArtifactEffects.scoreBonusPercent(j, Piece.L, false, true));
    }

    @Test
    void oUnique_allLineClearsScore_ignoresPieceType() {
        Artifact o = bare(Piece.O, 4);
        assertEquals(6f, ArtifactEffects.equippedScoreBonusPercent(o, true, false));
        assertEquals(0f, ArtifactEffects.equippedScoreBonusPercent(o, false, true));
        // Not piece-specific score for O clears
        assertEquals(0f, ArtifactEffects.scoreBonusPercent(o, Piece.O, true, false));
    }

    @Test
    void szUnique_scalesSpinScoreWithLevel() {
        assertEquals(20f, ArtifactEffects.scoreBonusPercent(bare(Piece.S, 1), Piece.S, false, true));
        assertEquals(60f, ArtifactEffects.scoreBonusPercent(bare(Piece.Z, 3), Piece.Z, false, true));
    }

    @Test
    void tUnique_scalesSpinMeterWithLevel() {
        Artifact t = bare(Piece.T, 2);
        assertEquals(50f, ArtifactEffects.pieceMeterBonusPercent(t, Piece.T, false, true));
        assertEquals(0f, ArtifactEffects.pieceMeterBonusPercent(t, Piece.T, true, false));
    }

    @Test
    void uniqueIgnoresBaseQualityAndIsNotInEffectsList() {
        Artifact a = new Artifact("x", Piece.I, 2, 99f);
        assertTrue(a.effects.isEmpty());
        assertEquals(40f, ArtifactUniqueEffects.of(a).percent(a.level));
        assertEquals(40f, ArtifactEffects.pieceMeterBonusPercent(a, Piece.I, true, false));
    }

    @Test
    void uniqueStacksWithRolledEffects() {
        Artifact a = bare(Piece.J, 1);
        a.effects.add(new ArtifactEffect(ArtifactEffectType.SPIN_SCORE, 10f)); // 2.0 * 10 = 20%
        assertEquals(40f, ArtifactEffects.scoreBonusPercent(a, Piece.J, false, true));
    }

    @Test
    void describeForUi_listsUniqueBeforeRolled() {
        Artifact a = bare(Piece.T, 2);
        a.effects.add(new ArtifactEffect(ArtifactEffectType.LINE_CLEAR_SCORE, 5f));
        String ui = a.describeForUi(5);
        assertTrue(ui.startsWith("T Artifact (Lv2)\n"));
        assertNotNull(a.uniqueEffect());
        assertTrue(ui.contains(a.uniqueEffect().describe(Piece.T, 2)));
        assertTrue(ui.contains(a.effects.get(0).describe(Piece.T)));
        int uniqueIdx = ui.indexOf(a.uniqueEffect().describe(Piece.T, 2));
        int rolledIdx = ui.indexOf(a.effects.get(0).describe(Piece.T));
        assertTrue(uniqueIdx < rolledIdx);
    }

    private static Artifact bare(byte pieceType, int level) {
        return new Artifact("id-" + pieceType + "-" + level, pieceType, level, 50f);
    }
}
