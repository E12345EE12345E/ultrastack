package me.ethanchen.lwjgl3.menuscreens.decorated;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;

import me.ethanchen.lwjgl3.menuscreens.ui.DesignUi;
import me.ethanchen.lwjgl3.menuscreens.ui.UIFont;
import me.ethanchen.lwjgl3.music.AudioManager;

/**
 * Rectangular action control: thick white frame, recolorable translucent fill (default white),
 * hover brightens the fill, and keyboard/controller selection uses breathing corner marks.
 */
public class DecoratedButton extends DecoratedElement {
    public String text;
    public Texture icon;
    /** Degrees clockwise; 180 draws the up-arrow asset as a down arrow. */
    public float iconRotationDeg;
    public Runnable action;
    public float fontSize = 1.7f;
    /** Panel tint; RGB is recolorable, default white at partial alpha. */
    public final Color fillColor = new Color(1f, 1f, 1f, 0.22f);
    public final Color outlineColor = new Color(1f, 1f, 1f, 1f);
    public float outlineDesignPx = OUTLINE_DESIGN_PX;
    /** Stays highlighted while true; used for mutually-exclusive option groups. */
    public boolean selected;
    /** When false, the button is visible and may show hover info, but ignores click and focus. */
    public boolean interactable = true;

    public DecoratedButton(float designX, float designY, float designW, float designH,
                           String text, Runnable action) {
        super(DesignUi.nx(designX), DesignUi.ny(designY), DesignUi.nw(designW), DesignUi.nh(designH));
        this.text = text;
        this.action = action;
        this.pressOnClick = true;
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
    public boolean isFocusable() {
        return interactable && super.isFocusable();
    }

    @Override
    public void activate() {
        if (!interactable || !visible || alpha <= 0.01f) return;
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
        if (!interactable || button != 0) return;
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

        boolean hot = (interactable && highlighted()) || selected;
        float a = Anim.clamp01(alpha) * (interactable ? 1f : 0.45f);
        drawFramedPanel(ctx, fillColor, outlineColor, a, hot, outlineDesignPx);

        ctx.sprites.begin();
        boolean hasIcon = icon != null;
        boolean hasText = text != null && !text.isEmpty();
        if (hasIcon && hasText) {
            drawIconAndText(ctx, x, y, w, h, a);
        } else if (hasIcon) {
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
            ctx.sprites.draw(icon, ix, iy, iw * 0.5f, ih * 0.5f, iw, ih, 1f, 1f,
                    iconRotationDeg, 0, 0, tw, th, false, false);
        } else if (hasText) {
            float[] saved = UIFont.saveAndSetScale(ctx.font, fontSize);
            GlyphLayout layout = new GlyphLayout(ctx.font, text);
            ctx.font.setColor(1f, 1f, 1f, a);
            ctx.font.draw(ctx.sprites, text,
                    x + (w - layout.width) * 0.5f,
                    y + (h + layout.height) * 0.5f);
            ctx.font.setColor(Color.WHITE);
            UIFont.restoreScale(ctx.font, saved);
        }
        ctx.sprites.setColor(Color.WHITE);
        ctx.sprites.end();
    }

    private void drawIconAndText(DecorContext ctx, float x, float y, float w, float h, float a) {
        float markBox = h * 0.72f;
        float markX = x + h * 0.14f;
        int tw = Math.max(1, icon.getWidth());
        int th = Math.max(1, icon.getHeight());
        float scale = markBox / Math.max(tw, th);
        float iw = tw * scale;
        float ih = th * scale;
        float iy = y + (h - ih) * 0.5f;
        ctx.sprites.setColor(1f, 1f, 1f, a);
        ctx.sprites.draw(icon, markX, iy, iw * 0.5f, ih * 0.5f, iw, ih, 1f, 1f,
                iconRotationDeg, 0, 0, tw, th, false, false);

        float[] saved = UIFont.saveAndSetScale(ctx.font, fontSize);
        GlyphLayout layout = new GlyphLayout(ctx.font, text);
        float textX = markX + iw + h * 0.16f;
        float textMaxW = x + w - textX - h * 0.14f;
        ctx.font.setColor(1f, 1f, 1f, a);
        if (layout.width > textMaxW && textMaxW > 0f) {
            ctx.font.draw(ctx.sprites, text, textX, y + (h + layout.height) * 0.5f, textMaxW,
                    com.badlogic.gdx.utils.Align.left, false);
        } else {
            ctx.font.draw(ctx.sprites, text, textX, y + (h + layout.height) * 0.5f);
        }
        ctx.font.setColor(Color.WHITE);
        UIFont.restoreScale(ctx.font, saved);
    }
}
