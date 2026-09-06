package me.ethanchen.lwjgl3.menuscreens.decorated;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import me.ethanchen.lwjgl3.menuscreens.MenuScreen;
import me.ethanchen.lwjgl3.menuscreens.ui.DesignUi;
import me.ethanchen.lwjgl3.menuscreens.ui.UIFont;
import me.ethanchen.lwjgl3.music.AudioManager;
import me.ethanchen.lwjgl3.render.MenuAssets;

/**
 * Rectangular action control: thick white frame, recolorable translucent fill (default white),
 * hover brightens the fill, and keyboard/controller selection uses breathing corner marks.
 */
public class DecoratedButton extends DecoratedElement {
    public String text;
    public Texture icon;
    public Runnable action;
    public float fontSize = 1.7f;
    /** Panel tint; RGB is recolorable, default white at partial alpha. */
    public final Color fillColor = new Color(1f, 1f, 1f, 0.22f);
    public final Color outlineColor = new Color(1f, 1f, 1f, 1f);

    private static final float OUTLINE_DESIGN_PX = 6f;

    public DecoratedButton(float designX, float designY, float designW, float designH,
                           String text, Runnable action) {
        super(DesignUi.nx(designX), DesignUi.ny(designY), DesignUi.nw(designW), DesignUi.nh(designH));
        this.text = text;
        this.action = action;
    }

    public static DecoratedButton icon(float designX, float designY, float designSize,
                                       Texture icon, Runnable action) {
        DecoratedButton b = new DecoratedButton(designX, designY, designSize, designSize, null, action);
        b.icon = icon;
        return b;
    }

    public DecoratedButton fill(float r, float g, float b) {
        fillColor.set(r, g, b, fillColor.a);
        return this;
    }

    public DecoratedButton fill(float r, float g, float b, float a) {
        fillColor.set(r, g, b, a);
        return this;
    }

    @Override
    public void activate() {
        if (!visible || alpha <= 0.01f) return;
        AudioManager.getInstance().playMenuPressSound();
        press = 1f;
        if (action != null) action.run();
    }

    @Override
    public void onClick() {
        activate();
    }

    @Override
    public void handleClick(int screenX, int screenY, int button) {
        if (button != 0) return;
        if (isClicked(screenX, screenY)) {
            activate();
        }
    }

    @Override
    public void renderDecorated(DecorContext ctx) {
        if (!visible || alpha <= 0.01f) return;

        float x = pxX();
        float y = pxY();
        float w = pxW();
        float h = pxH();

        float mouseX = Gdx.input.getX();
        float mouseY = Gdx.graphics.getHeight() - Gdx.input.getY();
        boolean wasHovered = hovered;
        hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        if (hovered && !wasHovered && isFocusable()) {
            AudioManager.getInstance().playMenuSelectSound();
        }

        boolean hot = highlighted();
        float a = Anim.clamp01(alpha);
        float t = Math.max(2f, MenuScreen.toScreenWidth((float) DesignUi.nw(OUTLINE_DESIGN_PX)));

        ctx.shapes.begin(ShapeRenderer.ShapeType.Filled);
        // After begin(): SpriteBatch can leave blend disabled, and ShapeRenderer does not restore it.
        enableBlend();
        // Fill first so alpha composites onto the real backdrop, not an opaque outline.
        float fillA = Math.min(0.65f, fillColor.a * (hot ? 1.55f : 1f));
        ctx.shapes.setColor(fillColor.r, fillColor.g, fillColor.b, fillA * a);
        ctx.shapes.rect(x, y, w, h);
        ctx.shapes.setColor(outlineColor.r, outlineColor.g, outlineColor.b, outlineColor.a * a);
        drawOutline(ctx.shapes, x, y, w, h, t);
        ctx.shapes.end();

        ctx.sprites.begin();
        if (icon != null) {
            float pad = Math.min(w, h) * 0.18f;
            float avail = Math.min(w, h) - 2f * pad;
            int tw = Math.max(1, icon.getWidth());
            int th = Math.max(1, icon.getHeight());
            float scale = Math.max(1f, (float) Math.floor(avail / Math.max(tw, th)));
            float iw = tw * scale;
            float ih = th * scale;
            float ix = Math.round(x + (w - iw) * 0.5f);
            float iy = Math.round(y + (h - ih) * 0.5f);
            ctx.sprites.setColor(1f, 1f, 1f, a);
            ctx.sprites.draw(icon, ix, iy, iw, ih);
        } else if (text != null && !text.isEmpty()) {
            float[] saved = UIFont.saveAndSetScale(ctx.font, fontSize);
            GlyphLayout layout = new GlyphLayout(ctx.font, text);
            ctx.font.setColor(1f, 1f, 1f, a);
            ctx.font.draw(ctx.sprites, text,
                    x + (w - layout.width) * 0.5f,
                    y + (h + layout.height) * 0.5f);
            ctx.font.setColor(Color.WHITE);
            UIFont.restoreScale(ctx.font, saved);
        }
        if (focused) {
            drawSelectionCorners(ctx, x, y, w, h, a);
        }
        ctx.sprites.setColor(Color.WHITE);
        ctx.sprites.end();
    }

    /** Axis-aligned frame that does not cover the translucent interior. */
    private static void drawOutline(ShapeRenderer shapes, float x, float y, float w, float h, float t) {
        shapes.rect(x - t, y - t, w + 2f * t, t);
        shapes.rect(x - t, y + h, w + 2f * t, t);
        shapes.rect(x - t, y, t, h);
        shapes.rect(x + w, y, t, h);
    }

    /**
     * {@code hovered_corner.png} is the top-left mark. The other three corners are the same
     * texture rotated in place. Size and inset follow {@code sin(menu time)} so they breathe.
     */
    private static void drawSelectionCorners(DecorContext ctx, float x, float y, float w, float h, float a) {
        Texture corner = MenuAssets.hoveredCorner();
        float wave = (float) Math.sin(ctx.menuElapsedMs / 400.0);
        float size = MenuScreen.toScreenWidth((float) DesignUi.nw(28f * (1f + 0.04f * wave)));
        float outset = MenuScreen.toScreenWidth((float) DesignUi.nw(16f + 2.5f * wave));
        ctx.sprites.setColor(1f, 1f, 1f, a);
        drawCorner(ctx, corner, x - outset, y + h + outset, size, 0f);
        drawCorner(ctx, corner, x + w + outset, y + h + outset, size, -90f);
        drawCorner(ctx, corner, x + w + outset, y - outset, size, 180f);
        drawCorner(ctx, corner, x - outset, y - outset, size, 90f);
    }

    private static void drawCorner(DecorContext ctx, Texture corner,
                                   float originX, float originY, float size, float rotationDeg) {
        ctx.sprites.draw(corner,
                originX, originY - size,
                0f, size,
                size, size,
                1f, 1f,
                rotationDeg,
                0, 0, corner.getWidth(), corner.getHeight(),
                false, false);
    }
}
