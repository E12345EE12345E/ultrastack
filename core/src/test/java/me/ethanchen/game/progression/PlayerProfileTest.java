package me.ethanchen.game.progression;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlayerProfileTest {

    @Test
    void defaultProfileUnlocksOnlyCharactersZeroAndOne() {
        PlayerProfile p = PlayerProfile.defaultProfile();
        assertTrue(p.isCharacterUnlocked(0));
        assertTrue(p.isCharacterUnlocked(1));
        assertFalse(p.isCharacterUnlocked(2));
    }
}
