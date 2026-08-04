package me.ethanchen.game.progression;

import java.util.ArrayList;
import java.util.List;

/**
 * A player's persisted character-and-leveling state: which characters are unlocked, which
 * character/two artifacts are currently equipped, and the full artifact inventory
 * (implementation.md, Part 1 and Part 4). Stored server-side and synced to the owning client.
 */
public class PlayerProfile {
    /** Bit {@code i} set means character id {@code i} is unlocked. */
    public long unlockedCharacterBits;
    public int selectedCharacterId;
    /** Equipped artifact ids, length always 2; a null entry means that slot is empty. */
    public String[] equippedArtifactIds;
    public List<Artifact> inventory;

    /** No-arg constructor required for libGDX Json and Kryo deserialization. */
    public PlayerProfile() {
        this.equippedArtifactIds = new String[2];
        this.inventory = new ArrayList<>();
    }

    public boolean isCharacterUnlocked(int characterId) {
        if (characterId < 0 || characterId >= 63) return false;
        return (unlockedCharacterBits & (1L << characterId)) != 0;
    }

    public void unlockCharacter(int characterId) {
        unlockedCharacterBits |= (1L << characterId);
    }

    public Artifact findArtifact(String artifactId) {
        if (artifactId == null) return null;
        for (Artifact a : inventory) {
            if (artifactId.equals(a.id)) return a;
        }
        return null;
    }

    /** Sorts {@link #inventory} by star count (desc), then level (desc). */
    public void sortInventory() {
        inventory.sort(Artifact::compareForInventory);
    }

    /** New-account default: characters 0 and 1 unlocked (Part 4), empty inventory, no loadout. */
    public static PlayerProfile defaultProfile() {
        PlayerProfile p = new PlayerProfile();
        p.unlockCharacter(0);
        p.unlockCharacter(1);
        p.selectedCharacterId = 0;
        return p;
    }
}
