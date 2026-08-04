package me.ethanchen.lwjgl3.menuscreens.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import me.ethanchen.lwjgl3.menuscreens.MenuScreen;

/**
 * Icon tile built for character portraits, equip/fusion reference slots, and inventory grids.
 * Behaves like {@link UIButton} for hover/click, but draws an icon (or placeholder) with optional
 * selection border, grayscale, corner label, and translucent overlay — without callers having to
 * poke low-level draw state every frame beyond the public setters below.
 */
public class UIInventoryButton extends UIElement {
    public static final Color OVERLAY_EQUIPPED = new Color(0.55f, 1f, 0.15f, 0.35f);
    public static final Color OVERLAY_FUSION_QUEUED = new Color(1f, 0f, 0f, 0.35f);

    public Texture icon;
    public Runnable action;
    /** True while the pointer is over this tile (updated during {@link #render}). */
    public boolean hovered;
    /** Yellow border when this tile is the active highlight / selected character. */
    public boolean selected;
    /** Desaturates the icon and dims the border (locked character, disabled mode). */
    public boolean grayscale;
    /** Bottom-right label, e.g. {@code Lv1}. */
    public String cornerLabel;
    /** Translucent fill over the tile (equipped lime, fusion-queued red, etc.). */
    public Color overlayColor;
    /** Centred when {@link #icon} is null. */
    public String placeholderText;
    /**
     * Optional id of the artifact (or other model object) this tile represents; used by screens
     * to resolve hover/highlight without parallel id lists.
     */
    public String boundId;

    public UIInventoryButton(double x, double y, double w, double h, Texture icon, Runnable action) {
        super(x, y, w, h);
        this.icon = icon;
        this.action = action;
    }

    /** Simple-UI square tile (legacy relative size used for both axes; drawn square from width). */
    public UIInventoryButton(double x, double y, double size, Texture icon, Runnable action) {
        this(x, y, size, size, icon, action);
    }

    /** Aspect-locked square tile from 1920×1080 design pixels. */
    public static UIInventoryButton design(double centerXPx, double centerYPx, double sizePx,
                                           Texture icon, Runnable action) {
        return new UIInventoryButton(
                DesignUi.nx(centerXPx), DesignUi.ny(centerYPx),
                DesignUi.nw(sizePx), DesignUi.nh(sizePx),
                icon, action);
    }

    /** Clears icon / labels / binding; optionally sets a placeholder. */
    public void clearSlot(String placeholder) {
        icon = null;
        boundId = null;
        cornerLabel = null;
        placeholderText = placeholder;
    }

    /** Binds this tile to an artifact-like display (icon + id + level corner). */
    public void showItem(Texture itemIcon, String id, String levelLabel) {
        icon = itemIcon;
        boundId = id;
        cornerLabel = levelLabel;
        placeholderText = null;
    }

    @Override
    public void render(ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font) {
        float pxW;
        float pxH;
        AspectLockedViewport vp = AspectLockedViewport.current();
        if (vp != null) {
            pxW = MenuScreen.toScreenWidth((float) width);
            pxH = MenuScreen.toScreenHeight((float) height);
        } else {
            pxW = MenuScreen.toScreenWidth((float) width);
            pxH = pxW;
        }
        float pxX = MenuScreen.convertFromRelCoordsX((float) centerX) - 0.5f * pxW;
        float pxY = MenuScreen.toScreenYBottom((float) centerY) - 0.5f * pxH;

        float mouseX = Gdx.input.getX();
        float mouseY = Gdx.graphics.getHeight() - Gdx.input.getY();
        hovered = mouseX >= pxX && mouseX <= pxX + pxW && mouseY >= pxY && mouseY <= pxY + pxH;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.12f, 0.12f, 0.15f, 1f);
        shapes.rect(pxX, pxY, pxW, pxH);
        shapes.end();

        if (icon != null) {
            float iconSize = Math.min(pxW, pxH) * 0.8f;
            Color tint = grayscale ? new Color(0.4f, 0.4f, 0.4f, 1f) : Color.WHITE;
            sprites.begin();
            sprites.setColor(tint);
            sprites.draw(icon, pxX + (pxW - iconSize) * 0.5f, pxY + (pxH - iconSize) * 0.5f, iconSize, iconSize);
            sprites.setColor(Color.WHITE);
            sprites.end();
        } else if (placeholderText != null) {
            GlyphLayout layout = new GlyphLayout();
            float[] saved = UIFont.saveAndSetScale(font, 0.7f);
            layout.setText(font, placeholderText);
            sprites.begin();
            font.setColor(0.5f, 0.5f, 0.5f, 1f);
            font.draw(sprites, placeholderText,
                    pxX + (pxW - layout.width) * 0.5f, pxY + (pxH + layout.height) * 0.5f);
            sprites.end();
            font.setColor(Color.WHITE);
            UIFont.restoreScale(font, saved);
        }

        // Re-enable blending after SpriteBatch: it (and other UI) can leave GL blend disabled,
        // which makes ShapeRenderer ignore vertex alpha and draw overlays fully opaque.
        if (overlayColor != null || hovered) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }

        if (overlayColor != null) {
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(overlayColor.r, overlayColor.g, overlayColor.b, overlayColor.a);
            shapes.rect(pxX, pxY, pxW, pxH);
            shapes.end();
        }

        if (hovered) {
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(1f, 1f, 1f, 0.12f);
            shapes.rect(pxX, pxY, pxW, pxH);
            shapes.end();
        }

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(selected ? Color.YELLOW : (grayscale ? Color.GRAY : Color.WHITE));
        shapes.rect(pxX, pxY, pxW, pxH);
        shapes.end();

        if (cornerLabel != null && !cornerLabel.isEmpty()) {
            GlyphLayout layout = new GlyphLayout();
            float[] saved = UIFont.saveAndSetScale(font, 0.55f);
            layout.setText(font, cornerLabel);
            sprites.begin();
            font.setColor(Color.WHITE);
            font.draw(sprites, cornerLabel, pxX + pxW - layout.width - 2f, pxY + layout.height + 2f);
            sprites.end();
            UIFont.restoreScale(font, saved);
        }
    }

    @Override
    public void onClick() {
        if (action != null) action.run();
    }

    @Override
    public void handleClick(int screenX, int screenY) {
        if (isClicked(screenX, screenY)) onClick();
    }

    @Override
    public void handleKeyTyped(char key) {}
}
