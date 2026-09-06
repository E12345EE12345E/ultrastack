package me.ethanchen.lwjgl3.menuscreens.decorated;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;

import me.ethanchen.lwjgl3.menuscreens.ui.DesignUi;

/**
 * Non-focusable texture mark. Size defaults to the texture's native pixel size in design
 * space; {@link #scaleMult} is the intended 1×/2×/3× multiplier.
 */
public class DecoratedImage extends DecoratedElement {
    public Texture texture;
    public Color tint = Color.WHITE;

    public DecoratedImage(float designX, float designY, Texture texture) {
        super(DesignUi.nx(designX), DesignUi.ny(designY),
                DesignUi.nw(texture != null ? texture.getWidth() : 1),
                DesignUi.nh(texture != null ? texture.getHeight() : 1));
        this.texture = texture;
        this.focusable = false;
    }

    @Override
    public boolean isFocusable() {
        return false;
    }

    @Override
    public void renderDecorated(DecorContext ctx) {
        if (!visible || alpha <= 0.01f || texture == null) return;
        float a = Anim.clamp01(alpha);
        ctx.sprites.begin();
        ctx.sprites.setColor(tint.r, tint.g, tint.b, tint.a * a);
        ctx.sprites.draw(texture, pxX(), pxY(), pxW(), pxH());
        ctx.sprites.setColor(Color.WHITE);
        ctx.sprites.end();
    }
}
