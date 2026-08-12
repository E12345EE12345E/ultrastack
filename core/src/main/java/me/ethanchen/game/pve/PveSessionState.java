package me.ethanchen.game.pve;

/**
 * Immutable snapshot of the level/difficulty selected for one PvE session, captured at
 * {@code ServerGame.startGame} time so a lobby setting change mid-game never affects the current
 * game (mirrors {@code ActiveLoadout}'s per-session character snapshot).
 */
public final class PveSessionState {
    public final int levelId;
    public final int difficulty;
    public final PveLevelData levelData;
    public final PveLootTable loot;

    public PveSessionState(int levelId, int difficulty, PveLevelData levelData, PveLootTable loot) {
        this.levelId = levelId;
        this.difficulty = difficulty;
        this.levelData = levelData;
        this.loot = loot;
    }

    /** Resolves a session state from the registry, or {@code null} if the level/difficulty isn't registered. */
    public static PveSessionState fromRegistry(int levelId, int difficulty) {
        PveLevelRegistry.Entry entry = PveLevelRegistry.byId(levelId);
        if (entry == null) return null;
        PveLevelData data = entry.load(difficulty);
        if (data == null) return null;
        return new PveSessionState(levelId, difficulty, data, entry.loot);
    }
}
