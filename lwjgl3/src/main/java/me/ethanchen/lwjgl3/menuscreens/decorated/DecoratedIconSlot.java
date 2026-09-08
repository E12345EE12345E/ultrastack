package me.ethanchen.lwjgl3.menuscreens.decorated;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import me.ethanchen.lwjgl3.menuscreens.ui.DesignUi;

/** Framed icon tile (character portrait or artifact). Hover uses {@link #info(String)}. */
public class DecoratedIconSlot extends DecoratedElement {
    private static final Color FILL = new Color(1f, 1f, 1f, 0.16f);
    private static final Color OUT = new Color(1f, 1f, 1f, 0.60f);

    public Texture texture;

    public DecoratedIconSlot(float designX, float designY, float designSize) {
        super(DesignUi.nx(designX), DesignUi.ny(designY), DesignUi.nw(designSize), DesignUi.nh(designSize));
        this.focusable = false;
    }

    @Override
    public boolean isFocusable() {
        return false;
    }

    @Override
    public void renderDecorated(DecorContext ctx) {
        if (!visible || alpha <= 0.01f) return;
        float a = Anim.clamp01(alpha);
        float x = pxX();
        float y = pxY();
        float w = pxW();
        float h = pxH();

        ctx.shapes.begin(ShapeRenderer.ShapeType.Filled);
        enableBlend();
        ctx.shapes.setColor(FILL.r, FILL.g, FILL.b, FILL.a * a);
        ctx.shapes.rect(x, y, w, h);
        ctx.shapes.setColor(OUT.r, OUT.g, OUT.b, OUT.a * a);
        drawAxisOutline(ctx.shapes, x, y, w, h, Math.max(1f, h * 0.04f));
        ctx.shapes.end();

        if (texture == null) return;
        ctx.sprites.begin();
        ctx.sprites.setColor(1f, 1f, 1f, a);
        ctx.sprites.draw(texture, x, y, w, h);
        ctx.sprites.setColor(Color.WHITE);
        ctx.sprites.end();
    }
}
