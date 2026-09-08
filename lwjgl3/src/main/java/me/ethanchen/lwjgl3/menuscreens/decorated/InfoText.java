package me.ethanchen.lwjgl3.menuscreens.decorated;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;

import me.ethanchen.lwjgl3.menuscreens.MenuScreen;
import me.ethanchen.lwjgl3.menuscreens.ui.DesignUi;
import me.ethanchen.lwjgl3.menuscreens.ui.UIFont;

/**
 * Optional hover popup for a {@link DecoratedElement}. Mouse placement follows the cursor
 * (flip left / above when the preferred bottom-right pose would clip). Keyboard/controller
 * placement is centered under the host, flipping above and sliding horizontally to stay on
 * screen. When both pointers apply, the mouse pose wins.
 */
public final class InfoText {
    public String text;
    public Color color = Color.WHITE;
    /** When false, only a mouse hover shows this popup — focus alone does not. */
    public boolean showOnFocus = true;
    public float fontSize = 1.35f;

    private static final Color BACK = new Color(0.18f, 0.18f, 0.18f, 1f);
    private static final Color OUTLINE = new Color(0.55f, 0.55f, 0.55f, 1f);
    private static final float PAD_DESIGN = 10f;
    private static final float GAP_DESIGN = 14f;
    private static final float MAX_WRAP_DESIGN = 420f;
    private static final float OUTLINE_PX = 1.5f;

    public InfoText(String text) {
        this.text = text;
    }

    public InfoText(String text, boolean showOnFocus) {
        this.text = text;
        this.showOnFocus = showOnFocus;
    }

    public static InfoText of(String text) {
        return new InfoText(text);
    }

    public boolean hasText() {
        return text != null && !text.isEmpty();
    }

    /**
     * @param followMouse {@code true} to anchor on the cursor; {@code false} to anchor on {@code host}
     */
    public void render(DecorContext ctx, DecoratedElement host, boolean followMouse) {
        if (ctx == null || host == null || !hasText()) return;

        float[] saved = UIFont.saveAndSetScale(ctx.font, fontSize);
        boolean useMarkup = text.indexOf("[#") >= 0;
        boolean prevMarkup = ctx.font.getData().markupEnabled;
        ctx.font.getData().markupEnabled = useMarkup;

        float maxW = MenuScreen.toScreenWidth((float) DesignUi.nw(MAX_WRAP_DESIGN));
        GlyphLayout layout = new GlyphLayout();
        layout.setText(ctx.font, text, color, maxW, Align.left, true);

        float pad = MenuScreen.toScreenWidth((float) DesignUi.nw(PAD_DESIGN));
        float gap = MenuScreen.toScreenWidth((float) DesignUi.nw(GAP_DESIGN));
        float boxW = layout.width + pad * 2f;
        float boxH = layout.height + pad * 2f;
        float screenW = Gdx.graphics.getWidth();
        float screenH = Gdx.graphics.getHeight();

        float boxX;
        float boxY;
        if (followMouse) {
            float cursorX = Gdx.input.getX();
            float cursorY = screenH - Gdx.input.getY();
            boxX = cursorX + gap;
            boxY = cursorY - gap - boxH;
            if (boxX + boxW > screenW) {
                boxX = cursorX - gap - boxW;
            }
            if (boxY < 0f) {
                boxY = cursorY + gap;
            }
        } else {
            float hostX = host.pxX();
            float hostY = host.pxY();
            float hostW = host.pxW();
            float hostH = host.pxH();
            boxX = hostX + (hostW - boxW) * 0.5f;
            boxY = hostY - gap - boxH;
            if (boxY < 0f) {
                boxY = hostY + hostH + gap;
            }
        }
        if (boxX < 0f) boxX = 0f;
        if (boxX + boxW > screenW) boxX = Math.max(0f, screenW - boxW);
        if (boxY < 0f) boxY = 0f;
        if (boxY + boxH > screenH) boxY = Math.max(0f, screenH - boxH);

        DecoratedElement.enableBlend();
        ctx.shapes.begin(ShapeRenderer.ShapeType.Filled);
        ctx.shapes.setColor(BACK);
        ctx.shapes.rect(boxX, boxY, boxW, boxH);
        ctx.shapes.setColor(OUTLINE);
        ctx.shapes.rect(boxX, boxY, boxW, OUTLINE_PX);
        ctx.shapes.rect(boxX, boxY + boxH - OUTLINE_PX, boxW, OUTLINE_PX);
        ctx.shapes.rect(boxX, boxY, OUTLINE_PX, boxH);
        ctx.shapes.rect(boxX + boxW - OUTLINE_PX, boxY, OUTLINE_PX, boxH);
        ctx.shapes.end();

        ctx.sprites.begin();
        ctx.font.setColor(color.r, color.g, color.b, color.a);
        ctx.font.draw(ctx.sprites, layout, boxX + pad, boxY + pad + layout.height);
        ctx.sprites.end();
        ctx.font.setColor(Color.WHITE);

        ctx.font.getData().markupEnabled = prevMarkup;
        UIFont.restoreScale(ctx.font, saved);
    }
}
