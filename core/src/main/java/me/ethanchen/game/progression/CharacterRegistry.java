package me.ethanchen.game.progression;

/**
 * All playable characters, indexed by id. New characters are added here as data only; nothing
 * elsewhere in the codebase should hardcode ids beyond referencing this registry.
 */
public final class CharacterRegistry {
    public static final CharacterDef[] ALL = {
            CharacterDef.THREE_MINO,
            CharacterDef.WIZARD,
    };

    private CharacterRegistry() {}

    public static CharacterDef byId(int id) {
        if (id < 0 || id >= ALL.length) return null;
        return ALL[id];
    }

    public static int count() {
        return ALL.length;
    }
}
