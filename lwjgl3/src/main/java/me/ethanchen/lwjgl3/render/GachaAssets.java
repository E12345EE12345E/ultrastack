package me.ethanchen.lwjgl3.render;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;

import me.ethanchen.game.progression.GachaTables;

/** Lazily cached Card Dealer textures. */
public final class GachaAssets {
    public static final int FACE = 0;
    public static final int MID_FLIP = 1;
    public static final int BACK = 2;

    private static final Map<Integer, Texture> CACHE = new HashMap<>();

    private GachaAssets() {}

    public static Texture cardTexture(byte rarity, int frame) {
        if (rarity < GachaTables.RARE || rarity > GachaTables.LEGENDARY) {
            throw new IllegalArgumentException("unknown card rarity " + rarity);
        }
        if (frame < FACE || frame > BACK) {
            throw new IllegalArgumentException("unknown card frame " + frame);
        }
        int index = rarity * 3 + frame;
        return CACHE.computeIfAbsent(index, i -> {
            Texture texture = new Texture(Gdx.files.internal("gacha/card_flip/card_" + i + ".png"));
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            return texture;
        });
    }
}
