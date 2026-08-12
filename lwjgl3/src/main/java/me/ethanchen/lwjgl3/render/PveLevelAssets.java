package me.ethanchen.lwjgl3.render;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;

/**
 * Cached PvE level thumbnail textures, mirroring {@link CharacterAssets}. Missing files return
 * {@code null} so callers can fall back to a hue-by-id filled rect
 * ({@code Color.fromHsv((137 * id) % 360, 1f, 1f)}).
 */
public final class PveLevelAssets {
    private static final Map<Integer, Texture> CACHE = new HashMap<>();
    private static final Map<Integer, Boolean> MISSING = new HashMap<>();

    private PveLevelAssets() {}

    /**
     * Returns the thumbnail texture for {@code levelId}, or {@code null} when no file exists at
     * {@code pve/thumbs/level_<id>.png}.
     */
    public static Texture thumbnailFor(int levelId) {
        if (MISSING.containsKey(levelId) && MISSING.get(levelId)) return null;
        if (CACHE.containsKey(levelId)) return CACHE.get(levelId);
        String path = "pve/thumbs/level_" + levelId + ".png";
        if (!Gdx.files.internal(path).exists()) {
            MISSING.put(levelId, true);
            return null;
        }
        Texture t = new Texture(Gdx.files.internal(path));
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        CACHE.put(levelId, t);
        MISSING.put(levelId, false);
        return t;
    }

    /** Fallback fill colour when no thumbnail texture is available (implementation.md, Part 1). */
    public static Color fallbackColor(int levelId) {
        Color c = new Color();
        c.fromHsv((137 * levelId) % 360, 1f, 1f);
        return c;
    }

    public static void dispose() {
        for (Texture t : CACHE.values()) t.dispose();
        CACHE.clear();
        MISSING.clear();
    }
}
