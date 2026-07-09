package me.ethanchen.lwjgl3.menuscreens.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import me.ethanchen.lwjgl3.menuscreens.MenuScreen;

public class UIButton extends UIElement {
    public String   text;
    public Runnable action;
    public boolean  hovered;
    public float    size = 1.0f;
    public float    glow = 0.0f;

    public UIButton(double x, double y, double w, double h, String text, Runnable action) {
        this(x, y, w, h, text, action, 1.0f);
    }

    public UIButton(double x, double y, double w, double h, String text, Runnable action, double size) {
        this(x, y, w, h, text, action, (float) size);
    }

    public UIButton(double x, double y, double w, double h, String text, Runnable action, float size) {
        super(x, y, w, h);
        this.text   = text;
        this.action = action;
        this.size   = size;
    }

    // -------------------------------------------------------------------------
    // Template methods — override in subclasses to customise appearance/state
    // -------------------------------------------------------------------------

    /** Whether the button is in an "active" state (e.g. waiting for key input). Default: false. */
    protected boolean isActive() { return false; }

    /**
     * When {@code true} the button's hover detection is suppressed (always {@code hovered=false}).
     * Default: false.
     */
    protected boolean suppressHover() { return false; }

    /** Background colour while idle (not active, not hovered). */
    protected Color idleBackgroundColor() { return new Color(0.15f, 0.15f, 0.2f, 1f); }

    /** Background colour while active. Ignored when {@link #isActive()} returns false. */
    protected Color activeBackgroundColor() { return idleBackgroundColor(); }

    /**
     * When {@code false} (default) the background is hidden while the button is hovered so the
     * glow overlay is the sole visual feedback, matching the standard UIButton look.
     * Set to {@code true} in bind-buttons that must always show their state colour.
     */
    protected boolean showBackgroundWhenHovered() { return false; }

    /** Border colour for the current frame. */
    protected Color currentBorderColor() { return hovered ? Color.CYAN : Color.WHITE; }

    /** Text colour for the current frame. */
    protected Color currentTextColor() { return Color.WHITE; }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    @Override
    public void render(ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font) {
        float pxWidth  = MenuScreen.convertFromRelCoordsX((float) width);
        float pxHeight = (float) (height * Gdx.graphics.getHeight());
        float pxX = MenuScreen.convertFromRelCoordsX((float) centerX) - 0.5f * pxWidth;
        float pxY = (Gdx.graphics.getHeight() - MenuScreen.convertFromRelCoordsY((float) centerY)) - 0.5f * pxHeight;

        float mouseX = Gdx.input.getX();
        float mouseY = Gdx.graphics.getHeight() - Gdx.input.getY();
        hovered = !suppressHover()
                && mouseX >= pxX && mouseX <= pxX + pxWidth
                && mouseY >= pxY && mouseY <= pxY + pxHeight;

        if (glow > 0f) glow = Math.max(0f, glow - 0.05f);

        boolean active  = isActive();
        boolean drawBg  = active || showBackgroundWhenHovered() || !hovered;

        Gdx.gl.glEnable(GL20.GL_BLEND);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (drawBg) {
            shapes.setColor(active ? activeBackgroundColor() : idleBackgroundColor());
            shapes.rect(pxX, pxY, pxWidth, pxHeight);
        }
        float totalGlowOpacity = (!active && hovered ? 0.15f : 0f) + glow * 0.5f;
        if (totalGlowOpacity > 0f) {
            shapes.setColor(1f, 1f, 1f, Math.min(1f, totalGlowOpacity));
            shapes.rect(pxX, pxY, pxWidth, pxHeight);
        }
        shapes.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(currentBorderColor());
        shapes.rect(pxX, pxY, pxWidth, pxHeight);
        shapes.end();

        float[] savedScale = UIFont.saveAndSetScale(font, size);
        sprites.begin();
        GlyphLayout layout = new GlyphLayout(font, text != null ? text : "");
        float textX = pxX + (pxWidth  - layout.width)  / 2f;
        float textY = pxY + (pxHeight + layout.height) / 2f;
        font.setColor(currentTextColor());
        font.draw(sprites, text != null ? text : "", textX, textY);
        sprites.end();
        UIFont.restoreScale(font, savedScale);
    }

    @Override
    public void onClick() {
        glow = 1.0f;
        if (action != null) action.run();
    }

    @Override
    public void handleClick(int screenX, int screenY) {
        if (isClicked(screenX, screenY)) onClick();
    }

    @Override
    public void handleKeyTyped(char c) {}
}
