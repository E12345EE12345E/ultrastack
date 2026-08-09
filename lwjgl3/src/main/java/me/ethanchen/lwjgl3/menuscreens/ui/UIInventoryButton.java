package me.ethanchen.lwjgl3.menuscreens.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import me.ethanchen.game.progression.Artifact;
import me.ethanchen.lwjgl3.menuscreens.MenuScreen;
import me.ethanchen.lwjgl3.music.AudioManager;

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
    /**
     * Fired on right-click or double left-click (e.g. quick add/remove from equip or fusion).
     * When null, those inputs fall back to {@link #action} for left double-click only if set —
     * right-click does nothing.
     */
    public Runnable secondaryAction;
    /** True while the pointer is over this tile (updated during {@link #render}). */
    public boolean hovered;
    /** Yellow border when this tile is the active highlight / selected character. */
    public boolean selected;
    /** Desaturates the icon and dims the border (locked character, disabled mode). */
    public boolean grayscale;
    /** Bottom-left label, e.g. rounded base quality {@code 0}–{@code 99}. */
    public String cornerLabelLeft;
    /**
     * Number of filled stars drawn in the bottom-right (0–5). Drawn with
     * {@link ShapeRenderer} so we don't depend on the UI font containing {@code ★}.
     */
    public int cornerStars;
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

    /**
     * Clears icon / labels / binding; optionally sets a placeholder.
     * Does not touch {@link #action} / {@link #secondaryAction} — those are interaction wiring
     * owned by the screen, not displayed content (callers that want a dead empty tile must
     * null the runnables themselves).
     */
    public void clearSlot(String placeholder) {
        icon = null;
        boundId = null;
        cornerLabelLeft = null;
        cornerStars = 0;
        placeholderText = placeholder;
    }

    /** Binds this tile to an icon with an optional bottom-left text badge (no stars). */
    public void showItem(Texture itemIcon, String id, String leftLabel, String rightLabelIgnored) {
        icon = itemIcon;
        boundId = id;
        cornerLabelLeft = leftLabel;
        cornerStars = 0;
        placeholderText = null;
    }

    /** Convenience for artifact tiles: quality bottom-left, level stars bottom-right. */
    public void showArtifact(Texture itemIcon, Artifact artifact) {
        icon = itemIcon;
        boundId = artifact.id;
        cornerLabelLeft = artifact.qualityLabel();
        cornerStars = artifact.levelStarCount();
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
        boolean wasHovered = hovered;
        hovered = mouseX >= pxX && mouseX <= pxX + pxW && mouseY >= pxY && mouseY <= pxY + pxH;
        if (hovered && !wasHovered) {
            AudioManager.getInstance().playMenuSelectSound();
        }

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

        if (cornerLabelLeft != null && !cornerLabelLeft.isEmpty()) {
            GlyphLayout layout = new GlyphLayout();
            float[] saved = UIFont.saveAndSetScale(font, 0.55f);
            layout.setText(font, cornerLabelLeft);
            sprites.begin();
            font.setColor(Color.WHITE);
            font.draw(sprites, cornerLabelLeft, pxX + 2f, pxY + layout.height + 2f);
            sprites.end();
            UIFont.restoreScale(font, saved);
        }

        int stars = Math.max(0, Math.min(5, cornerStars));
        if (stars > 0) {
            float outerR = Math.min(pxW, pxH) * 0.075f;
            float gap = outerR * 2.05f;
            float cy = pxY + outerR + 3f;
            float right = pxX + pxW - outerR - 3f;
            Color starColor = grayscale ? new Color(0.45f, 0.45f, 0.45f, 1f) : new Color(1f, 0.85f, 0.2f, 1f);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            for (int i = 0; i < stars; i++) {
                drawStar(shapes, right - (stars - 1 - i) * gap, cy, outerR, starColor);
            }
            shapes.end();
        }
    }

    /** Filled 5-point star centred at ({@code cx}, {@code cy}), tip pointing up (Y-up coords). */
    private static void drawStar(ShapeRenderer shapes, float cx, float cy, float outerR, Color color) {
        float innerR = outerR * 0.4f;
        shapes.setColor(color);
        for (int i = 0; i < 5; i++) {
            // +π/2 puts the first tip at +Y (up). -π/2 would point down in Y-up space.
            double a0 = Math.PI / 2.0 + i * (2.0 * Math.PI / 5.0);
            double a1 = a0 + Math.PI / 5.0;
            double a2 = a0 + 2.0 * Math.PI / 5.0;
            float ox0 = cx + (float) (Math.cos(a0) * outerR);
            float oy0 = cy + (float) (Math.sin(a0) * outerR);
            float ix  = cx + (float) (Math.cos(a1) * innerR);
            float iy  = cy + (float) (Math.sin(a1) * innerR);
            float ox1 = cx + (float) (Math.cos(a2) * outerR);
            float oy1 = cy + (float) (Math.sin(a2) * outerR);
            shapes.triangle(cx, cy, ox0, oy0, ix, iy);
            shapes.triangle(cx, cy, ix, iy, ox1, oy1);
        }
    }

    private static final long DOUBLE_CLICK_MS = 350L;
    private long lastLeftClickMs;

    @Override
    public void onClick() {
        if (action != null) action.run();
    }

    @Override
    public void handleClick(int screenX, int screenY) {
        handleClick(screenX, screenY, 0);
    }

    @Override
    public void handleClick(int screenX, int screenY, int button) {
        // Use the same screen-space rect as hover drawing. {@link #isClicked} goes through
        // relative+HDPI conversion which can disagree with that rect (especially near the top
        // of an aspect-locked canvas), so hover would light up but the click would miss.
        if (!containsScreenPoint(screenX, screenY)) return;
        if (button == Input.Buttons.RIGHT) {
            if (secondaryAction != null) {
                AudioManager.getInstance().playMenuPressSound();
                secondaryAction.run();
            }
            return;
        }
        if (button != Input.Buttons.LEFT) return;

        long now = System.currentTimeMillis();
        boolean isDouble = lastLeftClickMs > 0 && (now - lastLeftClickMs) <= DOUBLE_CLICK_MS;
        lastLeftClickMs = now;
        if (isDouble && secondaryAction != null) {
            AudioManager.getInstance().playMenuPressSound();
            secondaryAction.run();
            lastLeftClickMs = 0; // require a fresh pair for the next double-click
            return;
        }
        AudioManager.getInstance().playMenuPressSound();
        onClick();
    }

    /** Screen-space hit test matching the rectangle used for hover in {@link #render}. */
    private boolean containsScreenPoint(int screenX, int screenY) {
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
        float mouseX = screenX;
        float mouseY = Gdx.graphics.getHeight() - screenY;
        return mouseX >= pxX && mouseX <= pxX + pxW && mouseY >= pxY && mouseY <= pxY + pxH;
    }

    @Override
    public void handleKeyTyped(char key) {}
}
