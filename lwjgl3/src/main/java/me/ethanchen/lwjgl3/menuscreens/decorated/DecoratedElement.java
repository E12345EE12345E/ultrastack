package me.ethanchen.lwjgl3.menuscreens.decorated;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.Gdx;

import me.ethanchen.lwjgl3.menuscreens.MenuScreen;
import me.ethanchen.lwjgl3.menuscreens.ui.DesignUi;
import me.ethanchen.lwjgl3.menuscreens.ui.UIElement;

/**
 * Base for every decorated widget. Reuses {@link UIElement} relative-coordinate math and
 * hit-testing, then layers alpha, scale, design-pixel offsets, and focus state used by
 * animations and the controller/keyboard navigator.
 */
public abstract class DecoratedElement extends UIElement implements Decorated {
    public float alpha = 1f;
    public float scaleMult = 1f;
    /** Extra translation in 1920×1080 design pixels, applied after {@link #centerX}/{@link #centerY}. */
    public float offsetXDesign;
    public float offsetYDesign;
    public boolean visible = true;
    public boolean focusable = true;
    public boolean focused;
    public boolean hovered;
    /** Optional hover popup; null means no InfoText. */
    public InfoText infoText;
    /** 1 → 0 press-bounce envelope, decayed in {@link #updateDecorated(float)}. */
    public float press;

    public DecoratedElement(double x, double y, double w, double h) {
        super(x, y, w, h);
    }

    public DecoratedElement info(String text) {
        this.infoText = text == null || text.isEmpty() ? null : new InfoText(text);
        return this;
    }

    public DecoratedElement info(String text, boolean showOnFocus) {
        if (text == null || text.isEmpty()) {
            this.infoText = null;
            return this;
        }
        this.infoText = new InfoText(text, showOnFocus);
        return this;
    }

    public static DecoratedElement design(double designX, double designY, double designW, double designH,
                                          DecoratedElementFactory factory) {
        return factory.create(
                DesignUi.nx(designX), DesignUi.ny(designY),
                DesignUi.nw(designW), DesignUi.nh(designH));
    }

    @FunctionalInterface
    public interface DecoratedElementFactory {
        DecoratedElement create(double x, double y, double w, double h);
    }

    @Override
    public final void render(ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font) {
        DecorContext ctx = DecorContext.current();
        if (ctx == null || !visible || alpha <= 0.01f) return;
        renderDecorated(ctx);
    }

    public void updateDecorated(float dtS) {
        if (press > 0f) {
            press = Math.max(0f, press - dtS / 0.14f);
        }
    }

    @Override
    public boolean isFocusable() {
        return focusable && visible && alpha > 0.35f;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public void activate() {
        press = 1f;
        onClick();
    }

    @Override
    public void onClick() {}

    @Override
    public void handleClick(int screenX, int screenY) {
        handleClick(screenX, screenY, 0);
    }

    @Override
    public void handleClick(int screenX, int screenY, int button) {
        if (button != 0) return;
        if (isClicked(screenX, screenY)) {
            activate();
        }
    }

    @Override
    public void handleKeyTyped(char key) {}

    @Override
    public boolean isClicked(int screenX, int screenY) {
        if (!visible || alpha <= 0.01f) return false;
        Vector2 v = MenuScreen.convertToRelCoords(screenX, screenY);
        float cx = relCenterX();
        float cy = relCenterY();
        float w = relWidth();
        float h = relHeight();
        return v.x >= cx - 0.5f * w && v.x <= cx + 0.5f * w
                && v.y >= cy - 0.5f * h && v.y <= cy + 0.5f * h;
    }

    public boolean containsScreenPoint(int screenX, int screenY) {
        return isClicked(screenX, screenY);
    }

    protected float drawScale() {
        return scaleMult * (1f - 0.07f * press);
    }

    protected float relCenterX() {
        return (float) centerX + (float) DesignUi.nw(offsetXDesign);
    }

    protected float relCenterY() {
        return (float) centerY + (float) DesignUi.nh(offsetYDesign);
    }

    protected float relWidth() {
        return (float) width * drawScale();
    }

    protected float relHeight() {
        return (float) height * drawScale();
    }

    protected float pxW() {
        return MenuScreen.toScreenWidth(relWidth());
    }

    protected float pxH() {
        return MenuScreen.toScreenHeight(relHeight());
    }

    protected float pxX() {
        return MenuScreen.convertFromRelCoordsX(relCenterX()) - 0.5f * pxW();
    }

    protected float pxY() {
        return MenuScreen.toScreenYBottom(relCenterY()) - 0.5f * pxH();
    }

    /** 0–1 pulse used for focus/hover glow; driven by menu time so it stays in sync. */
    protected float focusPulse(long menuElapsedMs) {
        return 0.55f + 0.45f * (float) Math.sin(menuElapsedMs / 220.0);
    }

    /** Mouse-over chrome only — clears as soon as the pointer leaves. */
    protected boolean highlighted() {
        return hovered;
    }

    protected static void enableBlend() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    protected static void fillRoundRect(ShapeRenderer shapes, float x, float y, float w, float h,
                                        float r, Color color) {
        if (w <= 0f || h <= 0f) return;
        r = Math.min(r, Math.min(w, h) * 0.5f);
        shapes.setColor(color);
        shapes.rect(x + r, y, w - 2f * r, h);
        shapes.rect(x, y + r, r, h - 2f * r);
        shapes.rect(x + w - r, y + r, r, h - 2f * r);
        shapes.circle(x + r, y + r, r);
        shapes.circle(x + w - r, y + r, r);
        shapes.circle(x + r, y + h - r, r);
        shapes.circle(x + w - r, y + h - r, r);
    }
}
