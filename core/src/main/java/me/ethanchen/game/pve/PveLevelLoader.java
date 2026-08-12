package me.ethanchen.game.pve;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;

/**
 * Loads {@link PveLevelData} from JSON, using libGDX {@code Json} the same way as
 * {@code AccountStore}/{@code SettingsManager}. Level files live under {@code assets/pve/levels/}.
 */
public final class PveLevelLoader {

    private PveLevelLoader() {}

    /** Loads a level from an internal asset path, e.g. {@code "pve/levels/level_0_normal.json"}. */
    public static PveLevelData load(String internalPath) {
        FileHandle fh = Gdx.files.internal(internalPath);
        return fromJson(fh.readString("UTF-8"));
    }

    /**
     * Parses a level directly from a JSON string. Used by {@link #load(String)} and by unit
     * tests, which don't have a live {@code Gdx.files} backend available.
     */
    public static PveLevelData fromJson(String json) {
        Json json1 = new Json();
        return json1.fromJson(PveLevelData.class, json);
    }
}
