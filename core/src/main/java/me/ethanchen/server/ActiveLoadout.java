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
             + ArtifactEffects.scoreBonusPercent(artifactB, pieceType, lineClear, spin);
    }

    public float pieceMeterBonusPercent(byte pieceType, boolean lineClear, boolean spin) {
        return ArtifactEffects.pieceMeterBonusPercent(artifactA, pieceType, lineClear, spin)
             + ArtifactEffects.pieceMeterBonusPercent(artifactB, pieceType, lineClear, spin);
    }

    public float equippedMeterBonusPercent(boolean lineClear, boolean spin) {
        return ArtifactEffects.equippedMeterBonusPercent(artifactA, lineClear, spin)
             + ArtifactEffects.equippedMeterBonusPercent(artifactB, lineClear, spin);
    }

    public float equippedPassiveFillBonusPercent() {
        return ArtifactEffects.equippedPassiveFillBonusPercent(artifactA)
             + ArtifactEffects.equippedPassiveFillBonusPercent(artifactB);
    }
}
