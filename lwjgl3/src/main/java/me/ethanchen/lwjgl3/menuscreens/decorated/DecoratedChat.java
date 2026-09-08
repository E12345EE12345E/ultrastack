package me.ethanchen.lwjgl3.menuscreens.decorated;

import java.util.ArrayDeque;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.utils.Align;

import me.ethanchen.lwjgl3.menuscreens.ui.DesignUi;
import me.ethanchen.lwjgl3.menuscreens.ui.UIFont;

/**
 * Non-focusable chat log: framed panel, word-wrapped lines, libGDX color markup.
 * Sender names are tinted with a stable hue; user text is markup-escaped.
 */
public class DecoratedChat extends DecoratedElement {
    private static final int MAX_LINES = 40;

    public float fontSize = 1.2f;
    public final Color fillColor = new Color(1f, 1f, 1f, 0.16f);
    public final Color outlineColor = new Color(1f, 1f, 1f, 1f);

    private final ArrayDeque<String> lines = new ArrayDeque<>();
    private final GlyphLayout layout = new GlyphLayout();

    public DecoratedChat(float designX, float designY, float designW, float designH) {
        super(DesignUi.nx(designX), DesignUi.ny(designY), DesignUi.nw(designW), DesignUi.nh(designH));
        this.focusable = false;
    }

    @Override
    public boolean isFocusable() {
        return false;
    }

    public void append(String sender, String message) {
        String name = sender != null ? sender : "";
        String body = message != null ? message : "";
        String hex = hexFromHue(DecoratedScrollableList.hueFromKey(name));
        lines.add("[#" + hex + "]" + escapeMarkup(name) + "[]: " + escapeMarkup(body));
        while (lines.size() > MAX_LINES) {
            lines.removeFirst();
        }
    }

    public void clear() {
        lines.clear();
    }

    @Override
    public void renderDecorated(DecorContext ctx) {
        if (!visible || alpha <= 0.01f) return;

        float x = pxX();
        float y = pxY();
        float w = pxW();
        float h = pxH();
        float a = Anim.clamp01(alpha);
        drawFramedPanel(ctx, fillColor, outlineColor, a, false);

        if (lines.isEmpty()) return;

        String text = String.join("\n", lines);
        float pad = Math.max(10f, h * 0.06f);
        float innerX = x + pad;
        float innerY = y + pad;
        float innerW = Math.max(1f, w - 2f * pad);
        float innerH = Math.max(1f, h - 2f * pad);

        float[] saved = UIFont.saveAndSetScale(ctx.font, fontSize);
        boolean prevMarkup = ctx.font.getData().markupEnabled;
        ctx.font.getData().markupEnabled = true;
        ctx.font.setColor(1f, 1f, 1f, a);
        layout.setText(ctx.font, text, Color.WHITE, innerW, Align.left, true);

        float drawY = innerY + innerH;
        if (layout.height > innerH) {
            drawY = innerY + layout.height;
        }

        ctx.sprites.begin();
        ctx.sprites.flush();
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(Math.round(innerX), Math.round(innerY),
                Math.max(1, Math.round(innerW)), Math.max(1, Math.round(innerH)));
        ctx.font.draw(ctx.sprites, layout, innerX, drawY);
        ctx.sprites.flush();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        ctx.sprites.end();

        ctx.font.setColor(Color.WHITE);
        ctx.font.getData().markupEnabled = prevMarkup;
        UIFont.restoreScale(ctx.font, saved);
    }

    static String escapeMarkup(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        return raw.replace("[", "[[");
    }

    static String hexFromHue(float hue) {
        Color c = new Color().fromHsv(hue * 360f, 0.65f, 1f);
        int r = Math.round(c.r * 255f);
        int g = Math.round(c.g * 255f);
        int b = Math.round(c.b * 255f);
        return String.format("%02X%02X%02X", r, g, b);
    }
}
