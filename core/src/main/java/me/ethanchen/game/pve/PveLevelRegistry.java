package me.ethanchen.game.pve;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import me.ethanchen.game.board.Piece;
import me.ethanchen.game.progression.ArtifactRoller;

/**
 * Registry of every playable PvE level. Levels are registered once from a static initializer and
 * looked up by id at lobby-select / game-start time.
 */
public final class PveLevelRegistry {

    /** One registered level: its id, display name, one JSON path per difficulty, and its loot table. */
    public static final class Entry {
        public final int id;
        public final String name;
        public final String[] difficultyJsonPaths;
        public final PveLootTable loot;
        private final PveLevelData[] cache;

        private Entry(int id, String name, String[] difficultyJsonPaths, PveLootTable loot) {
            this.id = id;
            this.name = name;
            this.difficultyJsonPaths = difficultyJsonPaths;
            this.loot = loot;
            this.cache = new PveLevelData[difficultyJsonPaths.length];
        }

        public int difficultyCount() {
            return difficultyJsonPaths.length;
        }

        /** Loads (and caches) the level data for {@code difficulty}, or {@code null} if out of range. */
        public synchronized PveLevelData load(int difficulty) {
            if (difficulty < 0 || difficulty >= difficultyJsonPaths.length) return null;
            if (cache[difficulty] == null) {
                cache[difficulty] = PveLevelLoader.load(difficultyJsonPaths[difficulty]);
            }
            return cache[difficulty];
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    static {
        registerBuiltins();
    }

    private PveLevelRegistry() {}

    private static void registerBuiltins() {
        // Level 0: T-piece artifacts at random level 2 or 3 (implementation.md, Part 4).
        register(0, "Level 0", new String[]{ "pve/levels/level_0_normal.json" }, (rng, xp) -> {
            int level = rng.nextBoolean() ? 2 : 3;
            float baseQuality = 20f + rng.nextFloat() * 20f;
            return ArtifactRoller.roll(Piece.T, level, baseQuality, rng != null ? rng : new Random());
        });
    }

    /** Registers a new level. {@code id} must be unique and levels are expected to unlock in id order. */
    public static synchronized void register(int id, String name, String[] difficultyJsonPaths, PveLootTable loot) {
        ENTRIES.add(new Entry(id, name, difficultyJsonPaths, loot));
    }

    public static synchronized Entry byId(int id) {
        for (Entry e : ENTRIES) {
            if (e.id == id) return e;
        }
        return null;
    }

    public static synchronized int count() {
        return ENTRIES.size();
    }

    public static synchronized List<Entry> all() {
        return Collections.unmodifiableList(new ArrayList<>(ENTRIES));
    }

    /**
     * Test-only: clears every registered level so tests can register a controlled fixture set.
     * Call {@link #restoreBuiltinsForTesting()} afterwards if later tests need the shipping levels.
     */
    public static synchronized void clearForTesting() {
        ENTRIES.clear();
    }

    /** Test-only: re-registers the shipping built-in levels after {@link #clearForTesting()}. */
    public static synchronized void restoreBuiltinsForTesting() {
        ENTRIES.clear();
        registerBuiltins();
    }
}
