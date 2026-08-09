package me.ethanchen.game.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.board.Piece;

class PlayerProfileTest {

    @Test
    void newAccountProfile_hasStarterArtifactsAndEquipsIAndO() {
        PlayerProfile profile = PlayerProfile.newAccountProfile(new Random(1L));

        assertTrue(profile.isCharacterUnlocked(0));
        assertTrue(profile.isCharacterUnlocked(1));
        assertTrue(profile.isCharacterUnlocked(2));
        assertEquals(2, profile.selectedCharacterId);
        assertEquals(3, profile.inventory.size());

        Artifact i = findByType(profile, Piece.I);
        Artifact o = findByType(profile, Piece.O);
        Artifact t = findByType(profile, Piece.T);
        assertNotNull(i);
        assertNotNull(o);
        assertNotNull(t);

        assertEquals(2, i.level);
        assertEquals(30f, i.baseQuality);
        assertEquals(2, i.effects.size());

        assertEquals(2, o.level);
        assertEquals(20f, o.baseQuality);
        assertEquals(2, o.effects.size());

        assertEquals(2, t.level);
        assertEquals(15f, t.baseQuality);
        assertEquals(2, t.effects.size());

        assertEquals(i.id, profile.equippedArtifactIds[0]);
        assertEquals(o.id, profile.equippedArtifactIds[1]);
    }

    @Test
    void defaultProfile_hasEmptyInventory() {
        PlayerProfile profile = PlayerProfile.defaultProfile();
        assertTrue(profile.inventory.isEmpty());
        assertEquals(null, profile.equippedArtifactIds[0]);
        assertEquals(null, profile.equippedArtifactIds[1]);
        assertTrue(profile.isCharacterUnlocked(2));
        assertEquals(2, profile.selectedCharacterId);
        assertEquals(0L, profile.tokens);
    }

    @Test
    void ensureStarterCharactersUnlocked_unlocksAllThreeStarters() {
        PlayerProfile empty = new PlayerProfile();
        assertTrue(empty.ensureStarterCharactersUnlocked());
        assertTrue(empty.isCharacterUnlocked(CharacterDef.THREE_MINO.id));
        assertTrue(empty.isCharacterUnlocked(CharacterDef.WIZARD.id));
        assertTrue(empty.isCharacterUnlocked(CharacterDef.NOOB.id));
        assertFalse(empty.ensureStarterCharactersUnlocked());
    }

    @Test
    void ensureStarterCharactersUnlocked_addsMissingNoobToLegacyTwoCharacterProfiles() {
        PlayerProfile legacy = new PlayerProfile();
        legacy.unlockCharacter(CharacterDef.THREE_MINO.id);
        legacy.unlockCharacter(CharacterDef.WIZARD.id);
        legacy.selectedCharacterId = 0;

        assertTrue(legacy.ensureStarterCharactersUnlocked());
        assertTrue(legacy.isCharacterUnlocked(CharacterDef.NOOB.id));
        assertEquals(0, legacy.selectedCharacterId);
        assertFalse(legacy.ensureStarterCharactersUnlocked());
    }

    @Test
    void ensureTokensFromXp_seedsMissingTokensOnce() {
        PlayerProfile legacy = new PlayerProfile();
        assertEquals(null, legacy.tokens);

        assertTrue(legacy.ensureTokensFromXp(1500L));
        assertEquals(1500L, legacy.tokens);
        assertFalse(legacy.ensureTokensFromXp(9999L));
        assertEquals(1500L, legacy.tokens);
    }

    @Test
    void addTokens_incrementsSpendableBalance() {
        PlayerProfile profile = PlayerProfile.defaultProfile();
        profile.addTokens(40L);
        profile.addTokens(10L);
        assertEquals(50L, profile.tokens);
    }

    @Test
    void newAccountProfile_startsWithZeroTokens() {
        PlayerProfile profile = PlayerProfile.newAccountProfile(new Random(1L));
        assertEquals(0L, profile.tokens);
    }

    private static Artifact findByType(PlayerProfile profile, byte pieceType) {
        for (Artifact a : profile.inventory) {
            if (a.pieceType == pieceType) return a;
        }
        return null;
    }
}
