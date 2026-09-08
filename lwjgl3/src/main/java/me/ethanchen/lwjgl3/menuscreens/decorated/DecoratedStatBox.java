package me.ethanchen.lwjgl3.menuscreens.decorated;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import me.ethanchen.lwjgl3.menuscreens.ui.DesignUi;
import me.ethanchen.lwjgl3.menuscreens.ui.UIFont;

/** Framed title + value block used for profile best-score sections. */
public class DecoratedStatBox extends DecoratedElement {
    private static final Color FILL = new Color(1f, 1f, 1f, 0.16f);
    private static final Color OUT = new Color(1f, 1f, 1f, 0.85f);

    public String title;
    public String value = "—";
    public float titleSize = 1.05f;
    public float valueSize = 1.45f;

    public DecoratedStatBox(float designX, float designY, float designW, float designH, String title) {
        super(DesignUi.nx(designX), DesignUi.ny(designY), DesignUi.nw(designW), DesignUi.nh(designH));
        this.title = title;
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
        drawAxisOutline(ctx.shapes, x, y, w, h, Math.max(1f, h * 0.03f));
        ctx.shapes.end();

        ctx.sprites.begin();
        drawCentered(ctx, title != null ? title : "", x, y + h * 0.52f, w, h * 0.40f, titleSize, a);
        drawCentered(ctx, value != null ? value : "—", x, y + h * 0.10f, w, h * 0.42f, valueSize, a);
        ctx.sprites.end();
    }

    private static void drawCentered(DecorContext ctx, String text, float x, float y, float w, float h,
                                     float fontSize, float a) {
        float[] saved = UIFont.saveAndSetScale(ctx.font, fontSize);
        GlyphLayout layout = new GlyphLayout(ctx.font, text);
        ctx.font.setColor(1f, 1f, 1f, a);
        ctx.font.draw(ctx.sprites, text, x + (w - layout.width) * 0.5f, y + (h + layout.height) * 0.5f);
        ctx.font.setColor(Color.WHITE);
        UIFont.restoreScale(ctx.font, saved);
    }
}
