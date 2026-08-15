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

        private Entry(int id, String name, String[] difficultyJsonPaths, PveLootTable loot) {
            this.id = id;
            this.name = name;
            this.difficultyJsonPaths = difficultyJsonPaths;
            this.loot = loot;
        }

        public int difficultyCount() {
            return difficultyJsonPaths.length;
        }

        /**
         * Display name for {@code difficulty}, taken from the JSON filename: the text after the
         * last {@code _} and before {@code .json} (e.g. {@code level_0_normal.json} → {@code Normal}).
         * The first character is capitalized when it is a letter.
         */
        public String difficultyName(int difficulty) {
            if (difficulty < 0 || difficulty >= difficultyJsonPaths.length) return String.valueOf(difficulty + 1);
            String path = difficultyJsonPaths[difficulty];
            int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            String file = slash >= 0 ? path.substring(slash + 1) : path;
            int dot = file.lastIndexOf('.');
            if (dot >= 0) file = file.substring(0, dot);
            int under = file.lastIndexOf('_');
            String name = (under >= 0 && under < file.length() - 1) ? file.substring(under + 1) : file;
            if (!name.isEmpty() && Character.isLetter(name.charAt(0))) {
                return Character.toUpperCase(name.charAt(0)) + name.substring(1);
            }
            return name;
        }

        /**
         * Loads the level data for {@code difficulty} from disk, or {@code null} if out of range.
         * Reloads every call so JSON edits apply on the next game start without restarting the process.
         */
        public synchronized PveLevelData load(int difficulty) {
            if (difficulty < 0 || difficulty >= difficultyJsonPaths.length) return null;
            return PveLevelLoader.load(difficultyJsonPaths[difficulty]);
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    static {
        registerBuiltins();
    }

    private PveLevelRegistry() {}

    private static void registerBuiltins() {
        // Level 0: T-piece artifacts at random level 2 or 3 (implementation.md, Part 4).
        register(0, "Level 0", new String[]{ "pve/levels/level_0_normal.json", "pve/levels/level_0_hard.json" }, (rng, xp, difficulty) -> {
            int level = difficulty == 0 ? 1 : 2;
            float baseQuality = (difficulty == 0 ? 60f : 20f) + rng.nextFloat() * 20f;
            return ArtifactRoller.roll((byte) rng.nextInt(7), level, baseQuality, rng);
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
