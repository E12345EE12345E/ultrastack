package me.ethanchen.lwjgl3.render;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;

/**
 * Shared texture cache for decorated-menu chrome (logos and icon buttons).
 * Linear filtering keeps the marks clean when drawn at 2×–3×.
 */
public final class MenuAssets {
    private MenuAssets() {}

    private static final Map<String, Texture> CACHE = new HashMap<>();

    private static Texture load(String file) {
        return CACHE.computeIfAbsent(file, f -> {
            Texture t = new Texture(Gdx.files.internal(f));
            t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            return t;
        });
    }

    public static Texture ultrastackU() {
        return load("ultrastack_u.png");
    }

    public static Texture ultrastackS() {
        return load("ultrastack_s.png");
    }

    public static Texture settingsIcon() {
        return loadNearest("settings_icon.png");
    }

    public static Texture wikiIcon() {
        return loadNearest("info_icon.png");
    }

    public static Texture exitIcon() {
        return loadNearest("exit_icon.png");
    }

    /** Top-left selection corner; drawn four times (rotated) around a keyboard/controller-focused button. */
    public static Texture hoveredCorner() {
        return loadNearest("hovered_corner.png");
    }

    private static Texture loadNearest(String file) {
        return CACHE.computeIfAbsent(file, f -> {
            Texture t = new Texture(Gdx.files.internal(f));
            t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            return t;
        });
    }
}
