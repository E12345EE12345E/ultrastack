package me.ethanchen.lwjgl3.menuscreens.decorated;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import me.ethanchen.lwjgl3.menuscreens.ui.DesignUi;
import me.ethanchen.lwjgl3.menuscreens.ui.UIFont;

/** Tinted rectangle with a white level number. */
public class DecoratedLevelBadge extends DecoratedElement {
    private static final Color GRAY = new Color(0.48f, 0.48f, 0.50f, 1f);
    private static final Color GRAY_OUT = new Color(0.22f, 0.22f, 0.24f, 1f);
    private static final Color BLUE = new Color(0.55f, 0.80f, 1.00f, 1f);
    private static final Color BLUE_OUT = new Color(0.18f, 0.42f, 0.88f, 1f);
    private static final Color YELLOW = new Color(1.00f, 0.86f, 0.22f, 1f);
    private static final Color YELLOW_OUT = new Color(0.72f, 0.52f, 0.08f, 1f);

    public int level = 1;
    public float fontSize = 1.7f;

    public DecoratedLevelBadge(float designX, float designY, float designW, float designH) {
        super(DesignUi.nx(designX), DesignUi.ny(designY), DesignUi.nw(designW), DesignUi.nh(designH));
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

        Color fill = GRAY;
        Color outline = GRAY_OUT;
        if (level >= 20) {
            fill = YELLOW;
            outline = YELLOW_OUT;
        } else if (level >= 10) {
            fill = BLUE;
            outline = BLUE_OUT;
        }

        ctx.shapes.begin(ShapeRenderer.ShapeType.Filled);
        enableBlend();
        ctx.shapes.setColor(fill.r, fill.g, fill.b, fill.a * a);
        ctx.shapes.rect(x, y, w, h);
        ctx.shapes.setColor(outline.r, outline.g, outline.b, outline.a * a);
        drawAxisOutline(ctx.shapes, x, y, w, h, Math.max(2f, h * 0.08f));
        ctx.shapes.end();

        String label = String.valueOf(level);
        float[] saved = UIFont.saveAndSetScale(ctx.font, fontSize);
        GlyphLayout layout = new GlyphLayout(ctx.font, label);
        ctx.sprites.begin();
        ctx.font.setColor(1f, 1f, 1f, a);
        ctx.font.draw(ctx.sprites, label, x + (w - layout.width) * 0.5f, y + (h + layout.height) * 0.5f);
        ctx.font.setColor(Color.WHITE);
        ctx.sprites.end();
        UIFont.restoreScale(ctx.font, saved);
    }
}
