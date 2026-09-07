package me.ethanchen.lwjgl3.menuscreens.decorated;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.utils.Clipboard;

import me.ethanchen.lwjgl3.menuscreens.ui.DesignUi;
import me.ethanchen.lwjgl3.menuscreens.ui.UIFont;
import me.ethanchen.util.TextSanitizer;

/**
 * Single-line text field using the same framed-panel chrome as {@link DecoratedButton}.
 * Typing focus is {@link #focused} from the screen navigator; clipboard shortcuts live in
 * {@link #handleKeyDown(int)}.
 */
public class DecoratedTextBox extends DecoratedElement {
    public static final int SANITIZE_NONE = 0;
    public static final int SANITIZE_CHAT = 1;
    public static final int SANITIZE_NAME = 2;
    public static final int SANITIZE_JOIN_CODE = 3;

    public String text = "";
    public int sanitize = SANITIZE_NONE;
    public Runnable runOnEnter;
    public float fontSize = 1.55f;
    public final Color fillColor = new Color(1f, 1f, 1f, 0.22f);
    public final Color outlineColor = new Color(1f, 1f, 1f, 1f);

    public DecoratedTextBox(float designX, float designY, float designW, float designH) {
        super(DesignUi.nx(designX), DesignUi.ny(designY), DesignUi.nw(designW), DesignUi.nh(designH));
    }

    public String get() {
        return text != null ? text : "";
    }

    public void set(String value) {
        text = value != null ? value : "";
        commitText();
    }

    public DecoratedTextBox sanitize(int mode) {
        this.sanitize = mode;
        return this;
    }

    public DecoratedTextBox onEnter(Runnable run) {
        this.runOnEnter = run;
        return this;
    }

    @Override
    public void activate() {
        // Focus only — Enter / click must not treat this like a button.
    }

    public boolean handleKeyDown(int keycode) {
        if (!focused || !isShortcutModifierDown()) return false;

        Clipboard clipboard = Gdx.app.getClipboard();
        switch (keycode) {
            case Input.Keys.C:
                if (!get().isEmpty()) {
                    clipboard.setContents(get());
                }
                return true;
            case Input.Keys.X:
                if (!get().isEmpty()) {
                    clipboard.setContents(get());
                    text = "";
                    commitText();
                }
                return true;
            case Input.Keys.V:
                String pasted = clipboard.getContents();
                if (pasted != null && !pasted.isEmpty()) {
                    pasted = pasted.replace('\r', ' ').replace('\n', ' ');
                    text = get() + pasted;
                    commitText();
                }
                return true;
            default:
                return false;
        }
    }

    @Override
    public void handleKeyTyped(char c) {
        if (!focused) return;
        if (isShortcutModifierDown()) return;
        if (c == '\b' || c == '\u0008') {
            String cur = get();
            if (!cur.isEmpty()) {
                text = cur.substring(0, cur.length() - 1);
            }
        } else if (c == '\r' || c == '\n' || c == '\t' || c == '\u0009') {
            return;
        } else if (c >= 32 && c < 127) {
            text = get() + c;
        }
        commitText();
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
        hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;

        boolean hot = highlighted();
        float a = Anim.clamp01(alpha);
        drawFramedPanel(ctx, fillColor, outlineColor, a, hot);

        ctx.sprites.begin();
        float[] saved = UIFont.saveAndSetScale(ctx.font, fontSize);
        String display = get();
        if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            display += "|";
        }
        GlyphLayout layout = new GlyphLayout(ctx.font, display);
        float pad = Math.max(10f, h * 0.18f);
        float textX = x + pad;
        float textY = y + (h + layout.height) * 0.5f;
        ctx.font.setColor(1f, 1f, 1f, a);
        ctx.font.draw(ctx.sprites, display, textX, textY);
        ctx.font.setColor(Color.WHITE);
        UIFont.restoreScale(ctx.font, saved);
        if (focused) {
            drawFocusCorners(ctx, x, y, w, h, a);
        }
        ctx.sprites.setColor(Color.WHITE);
        ctx.sprites.end();
    }

    private void commitText() {
        switch (sanitize) {
            case SANITIZE_CHAT:
                text = TextSanitizer.sanitizeChat(text);
                break;
            case SANITIZE_NAME:
                text = TextSanitizer.sanitizeName(text);
                break;
            case SANITIZE_JOIN_CODE:
                text = TextSanitizer.sanitizeJoinCode(text);
                break;
            default:
                if (text == null) text = "";
                break;
        }
    }

    private static boolean isShortcutModifierDown() {
        return Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
    }
}
