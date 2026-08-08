package me.ethanchen.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.board.Piece;
import me.ethanchen.game.progression.Artifact;
import me.ethanchen.game.progression.ArtifactEffect;
import me.ethanchen.game.progression.ArtifactEffectType;

class ActiveLoadoutBonusTest {

    @Test
    void pieceCardAddsEquippedBonusesOntoMatchingPiece() {
        Artifact i = bare(Piece.I, 1);
        i.effects.add(new ArtifactEffect(ArtifactEffectType.LINE_CLEAR_SCORE, 10f)); // +10%
        // Use J (spin-score unique) so equipped clear-score comes only from the rolled effect.
        Artifact j = bare(Piece.J, 1);
        j.effects.add(new ArtifactEffect(ArtifactEffectType.EQUIPPED_LINE_CLEAR_SCORE, 50f)); // +5%
        ActiveLoadout loadout = new ActiveLoadout(null, i, j);

        assertEquals(15f, loadout.clearScoreBonusPercent(Piece.I));
        assertEquals(5f, loadout.clearScoreBonusPercent(Piece.T));
        assertEquals(5f, loadout.clearScoreBonusPercent(Piece.I3));
        assertEquals(5f, loadout.equippedScoreBonusPercent(true, false));
    }

    @Test
    void meterBonusesSumPieceSpecificAndEquipped() {
        Artifact t = bare(Piece.T, 2); // unique spin meter +50%
        t.effects.add(new ArtifactEffect(ArtifactEffectType.EQUIPPED_SPIN_METER, 20f)); // +10%
        ActiveLoadout loadout = new ActiveLoadout(null, t, null);

        assertEquals(60f, loadout.spinMeterBonusPercent(Piece.T));
        assertEquals(10f, loadout.spinMeterBonusPercent(Piece.I));
        assertEquals(10f, loadout.equippedMeterBonusPercent(false, true));
    }

    private static Artifact bare(byte pieceType, int level) {
        return new Artifact("id-" + pieceType + "-" + level, pieceType, level, 50f);
    }
}
