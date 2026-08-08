package me.ethanchen.server;

import me.ethanchen.game.progression.Artifact;
import me.ethanchen.game.progression.ArtifactEffects;
import me.ethanchen.game.progression.CharacterDef;

/**
 * A resolved character + two equipped artifacts snapshot, captured once at game start
 * (implementation.md, Part 4) so mid-game loadout screen edits never affect an in-progress game.
 */
public final class ActiveLoadout {
    public final CharacterDef character;
    public final Artifact artifactA;
    public final Artifact artifactB;

    public ActiveLoadout(CharacterDef character, Artifact artifactA, Artifact artifactB) {
        this.character = character;
        this.artifactA = artifactA;
        this.artifactB = artifactB;
    }

    public float scoreBonusPercent(byte pieceType, boolean lineClear, boolean spin) {
        return ArtifactEffects.scoreBonusPercent(artifactA, pieceType, lineClear, spin)
             + ArtifactEffects.scoreBonusPercent(artifactB, pieceType, lineClear, spin)
             + ArtifactEffects.equippedScoreBonusPercent(artifactA, lineClear, spin)
             + ArtifactEffects.equippedScoreBonusPercent(artifactB, lineClear, spin);
    }

    public float pieceMeterBonusPercent(byte pieceType, boolean lineClear, boolean spin) {
        return ArtifactEffects.pieceMeterBonusPercent(artifactA, pieceType, lineClear, spin)
             + ArtifactEffects.pieceMeterBonusPercent(artifactB, pieceType, lineClear, spin);
    }

    public float equippedMeterBonusPercent(boolean lineClear, boolean spin) {
        return ArtifactEffects.equippedMeterBonusPercent(artifactA, lineClear, spin)
             + ArtifactEffects.equippedMeterBonusPercent(artifactB, lineClear, spin);
    }

    public float equippedScoreBonusPercent(boolean lineClear, boolean spin) {
        return ArtifactEffects.equippedScoreBonusPercent(artifactA, lineClear, spin)
             + ArtifactEffects.equippedScoreBonusPercent(artifactB, lineClear, spin);
    }

    public float equippedPassiveFillBonusPercent() {
        return ArtifactEffects.equippedPassiveFillBonusPercent(artifactA)
             + ArtifactEffects.equippedPassiveFillBonusPercent(artifactB);
    }

    /** Piece card: piece-specific + "all line clears" score bonuses (artifacts only). */
    public float clearScoreBonusPercent(byte pieceType) {
        return scoreBonusPercent(pieceType, true, false);
    }

    /** Piece card: piece-specific + "all spins" score bonuses (artifacts only). */
    public float spinScoreBonusPercent(byte pieceType) {
        return scoreBonusPercent(pieceType, false, true);
    }

    /** Piece card: piece-specific + equipped line-clear meter bonuses (artifacts only). */
    public float clearMeterBonusPercent(byte pieceType) {
        return pieceMeterBonusPercent(pieceType, true, false)
             + equippedMeterBonusPercent(true, false);
    }

    /** Piece card: piece-specific + equipped spin meter bonuses (artifacts only). */
    public float spinMeterBonusPercent(byte pieceType) {
        return pieceMeterBonusPercent(pieceType, false, true)
             + equippedMeterBonusPercent(false, true);
    }
}
