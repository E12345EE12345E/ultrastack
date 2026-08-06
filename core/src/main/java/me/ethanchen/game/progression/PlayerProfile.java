package me.ethanchen.game.progression;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import me.ethanchen.game.board.Piece;

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

    /**
     * Unlocks The Noob (id 2) if missing. Used to migrate profiles created before that character
     * existed. Returns {@code true} when bits changed and the profile should be re-persisted.
     */
    public boolean ensureNoobUnlocked() {
        int noobId = CharacterDef.NOOB.id;
        if (isCharacterUnlocked(noobId)) return false;
        unlockCharacter(noobId);
        return true;
    }

    public Artifact findArtifact(String artifactId) {
        if (artifactId == null) return null;
        for (Artifact a : inventory) {
            if (artifactId.equals(a.id)) return a;
        }
        return null;
    }

    /** Sorts {@link #inventory} by level (desc), then base quality (desc). */
    public void sortInventory() {
        inventory.sort(Artifact::compareForInventory);
    }

    /**
     * Fallback for blank/legacy {@code extra_json} rows: characters 0–2 unlocked (The Noob
     * selected), empty inventory, no loadout. New registrations use
     * {@link #newAccountProfile(Random)} instead.
     */
    public static PlayerProfile defaultProfile() {
        PlayerProfile p = new PlayerProfile();
        p.unlockCharacter(0);
        p.unlockCharacter(1);
        p.unlockCharacter(2);
        p.selectedCharacterId = 2;
        return p;
    }

    /**
     * Profile written for a freshly registered account: characters 0–2 unlocked with The Noob
     * selected, three level-2 starter artifacts (I/O/T) with rolled effects, I and O equipped.
     */
    public static PlayerProfile newAccountProfile(Random rng) {
        PlayerProfile p = defaultProfile();
        Artifact iArtifact = ArtifactRoller.roll(Piece.I, 2, 30f, rng);
        Artifact oArtifact = ArtifactRoller.roll(Piece.O, 2, 20f, rng);
        Artifact tArtifact = ArtifactRoller.roll(Piece.T, 2, 15f, rng);
        p.inventory.add(iArtifact);
        p.inventory.add(oArtifact);
        p.inventory.add(tArtifact);
        p.sortInventory();
        p.equippedArtifactIds[0] = iArtifact.id;
        p.equippedArtifactIds[1] = oArtifact.id;
        return p;
    }
}
