package me.ethanchen.lwjgl3.menuscreens.decorated;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;

import me.ethanchen.lwjgl3.menuscreens.ui.DesignUi;
import me.ethanchen.lwjgl3.menuscreens.ui.UIFont;
import me.ethanchen.lwjgl3.music.AudioManager;
import me.ethanchen.lwjgl3.render.MenuAssets;

/**
 * Focusable chip: UltraStack icon plus a username label.
 */
public class DecoratedAccountButton extends DecoratedElement {
    public String text;
    public Runnable action;
    public float fontSize = 1.35f;
    public final Color fillColor = new Color(0.95f, 0.32f, 0.68f, 0.22f);
    public final Color outlineColor = new Color(1f, 1f, 1f, 1f);

    public DecoratedAccountButton(float designX, float designY, float designW, float designH,
                                  String text, Runnable action) {
        super(DesignUi.nx(designX), DesignUi.ny(designY), DesignUi.nw(designW), DesignUi.nh(designH));
        this.text = text;
        this.action = action;
        this.pressOnClick = true;
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

        float a = Anim.clamp01(alpha);
        drawFramedPanel(ctx, fillColor, outlineColor, a, highlighted());

        Texture icon = MenuAssets.ultrastackIcon();
        float markBox = h * 0.72f;
        float markX = x + h * 0.14f;
        float markY = y + (h - markBox) * 0.5f;
        float markW = markBox;
        float markH = markBox;
        if (icon != null) {
            int tw = Math.max(1, icon.getWidth());
            int th = Math.max(1, icon.getHeight());
            float scale = markBox / Math.max(tw, th);
            markW = tw * scale;
            markH = th * scale;
            markY = y + (h - markH) * 0.5f;
        }

        String label = text != null ? text : "";
        ctx.sprites.begin();
        drawMark(ctx, icon, markX, markY, markW, markH, a);
        float[] saved = UIFont.saveAndSetScale(ctx.font, fontSize);
        GlyphLayout layout = new GlyphLayout(ctx.font, label);
        float textX = markX + markW + h * 0.16f;
        float textMaxW = x + w - textX - h * 0.16f;
        ctx.font.setColor(1f, 1f, 1f, a);
        ctx.font.draw(ctx.sprites, label,
                textX + Math.max(0f, (textMaxW - layout.width) * 0.5f),
                y + (h + layout.height) * 0.5f);
        ctx.font.setColor(Color.WHITE);
        UIFont.restoreScale(ctx.font, saved);
        if (focused) {
            drawFocusCorners(ctx, x, y, w, h, a);
        }
        ctx.sprites.setColor(Color.WHITE);
        ctx.sprites.end();
    }

    private static void drawMark(DecorContext ctx, Texture texture, float x, float y, float w, float h, float a) {
        if (texture == null) return;
        ctx.sprites.setColor(1f, 1f, 1f, a);
        ctx.sprites.draw(texture, x, y, w, h);
        ctx.sprites.setColor(Color.WHITE);
    }
}
