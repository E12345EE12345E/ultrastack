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

        boolean hot = highlighted() || selected;
        float a = Anim.clamp01(alpha);
        drawFramedPanel(ctx, fillColor, outlineColor, a, hot, outlineDesignPx);

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
            ctx.sprites.draw(icon, ix, iy, iw * 0.5f, ih * 0.5f, iw, ih, 1f, 1f,
                    iconRotationDeg, 0, 0, tw, th, false, false);
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
            drawFocusCorners(ctx, x, y, w, h, a);
        }
        ctx.sprites.setColor(Color.WHITE);
        ctx.sprites.end();
    }
}
