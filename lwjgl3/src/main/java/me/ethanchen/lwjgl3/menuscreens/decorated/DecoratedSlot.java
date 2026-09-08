package me.ethanchen.lwjgl3.menuscreens.decorated;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import me.ethanchen.game.progression.Artifact;
import me.ethanchen.lwjgl3.menuscreens.ui.DesignUi;
import me.ethanchen.lwjgl3.menuscreens.ui.UIFont;
import me.ethanchen.lwjgl3.music.AudioManager;

/**
 * Icon tile for character portraits, equip/fusion reference slots, and inventory grids.
 * Mouse clicks behave like a button; keyboard/controller focus is owned by a
 * {@link DecoratedGrid} unless {@link #focusable} is turned on (equip slots).
 */
public class DecoratedSlot extends DecoratedElement {
    public static final Color OVERLAY_EQUIPPED = new Color(0.55f, 1f, 0.15f, 0.35f);
    public static final Color OVERLAY_FUSION_QUEUED = new Color(1f, 0f, 0f, 0.35f);

    private static final Color FILL = new Color(0.12f, 0.12f, 0.15f, 0.92f);
    private static final Color OUTLINE = new Color(1f, 1f, 1f, 1f);
    private static final Color OUTLINE_SELECTED = new Color(1f, 0.85f, 0.2f, 1f);
    private static final Color OUTLINE_GRAY = new Color(0.45f, 0.45f, 0.48f, 1f);
    private static final long DOUBLE_CLICK_MS = 350L;

    public Texture icon;
    public Runnable action;
    public Runnable secondaryAction;
    public boolean selected;
    public boolean grayscale;
    public String cornerLabelLeft;
    public int cornerStars;
    public Color overlayColor;
    public String placeholderText;
    public String boundId;

    private long lastLeftClickMs;

    public DecoratedSlot(float designX, float designY, float designSize) {
        super(DesignUi.nx(designX), DesignUi.ny(designY), DesignUi.nw(designSize), DesignUi.nh(designSize));
        this.focusable = false;
        this.pressOnClick = true;
    }

    public void clearSlot(String placeholder) {
        icon = null;
        boundId = null;
        cornerLabelLeft = null;
        cornerStars = 0;
        placeholderText = placeholder;
        overlayColor = null;
        infoText = null;
    }

    public void showItem(Texture itemIcon, String id, String leftLabel) {
        icon = itemIcon;
        boundId = id;
        cornerLabelLeft = leftLabel;
        cornerStars = 0;
        placeholderText = null;
    }

    public void showArtifact(Texture itemIcon, Artifact artifact) {
        icon = itemIcon;
        boundId = artifact.id;
        cornerLabelLeft = artifact.qualityLabel();
        cornerStars = artifact.levelStarCount();
        placeholderText = null;
    }

    @Override
    public void handleClick(int screenX, int screenY, int button) {
        if (!visible || alpha <= 0.01f) return;
        if (!containsScreenPoint(screenX, screenY)) return;
        if (button == Input.Buttons.RIGHT) {
            if (secondaryAction != null) {
                AudioManager.getInstance().playMenuPressSound();
                press = 1f;
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
            press = 1f;
            secondaryAction.run();
            lastLeftClickMs = 0;
            return;
        }
        if (action == null) return;
        AudioManager.getInstance().playMenuPressSound();
        press = 1f;
        action.run();
    }

    @Override
    public void activate() {
        if (!visible || alpha <= 0.01f || action == null) return;
        AudioManager.getInstance().playMenuPressSound();
        press = 1f;
        action.run();
    }

    @Override
    public void onClick() {
        activate();
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
        if (hovered && !wasHovered) {
            AudioManager.getInstance().playMenuSelectSound();
        }

        float a = Anim.clamp01(alpha) * (grayscale ? 0.7f : 1f);
        Color outline = selected ? OUTLINE_SELECTED : (grayscale ? OUTLINE_GRAY : OUTLINE);
        boolean hot = highlighted() || selected || focused;
        drawFramedPanel(ctx, FILL, outline, a, hot, 3f);

        if (icon != null) {
            float iconSize = Math.min(w, h) * 0.8f;
            float ix = x + (w - iconSize) * 0.5f;
            float iy = y + (h - iconSize) * 0.5f;
            ctx.sprites.begin();
            if (grayscale) {
                ctx.sprites.setColor(0.4f, 0.4f, 0.4f, a);
            } else {
                ctx.sprites.setColor(1f, 1f, 1f, a);
            }
            ctx.sprites.draw(icon, ix, iy, iconSize, iconSize);
            ctx.sprites.setColor(Color.WHITE);
            ctx.sprites.end();
        } else if (placeholderText != null) {
            float[] saved = UIFont.saveAndSetScale(ctx.font, 0.7f);
            GlyphLayout layout = new GlyphLayout(ctx.font, placeholderText);
            ctx.sprites.begin();
            ctx.font.setColor(0.5f, 0.5f, 0.5f, a);
            ctx.font.draw(ctx.sprites, placeholderText,
                    x + (w - layout.width) * 0.5f, y + (h + layout.height) * 0.5f);
            ctx.font.setColor(Color.WHITE);
            ctx.sprites.end();
            UIFont.restoreScale(ctx.font, saved);
        }

        if (overlayColor != null || hovered) {
            enableBlend();
            ctx.shapes.begin(ShapeRenderer.ShapeType.Filled);
            if (overlayColor != null) {
                ctx.shapes.setColor(overlayColor.r, overlayColor.g, overlayColor.b, overlayColor.a * a);
                ctx.shapes.rect(x, y, w, h);
            }
            if (hovered) {
                ctx.shapes.setColor(1f, 1f, 1f, 0.12f * a);
                ctx.shapes.rect(x, y, w, h);
            }
            ctx.shapes.end();
        }

        if (cornerLabelLeft != null && !cornerLabelLeft.isEmpty()) {
            float[] saved = UIFont.saveAndSetScale(ctx.font, 0.55f);
            GlyphLayout layout = new GlyphLayout(ctx.font, cornerLabelLeft);
            ctx.sprites.begin();
            ctx.font.setColor(1f, 1f, 1f, a);
            ctx.font.draw(ctx.sprites, cornerLabelLeft, x + 4f, y + layout.height + 4f);
            ctx.font.setColor(Color.WHITE);
            ctx.sprites.end();
            UIFont.restoreScale(ctx.font, saved);
        }

        int stars = Math.max(0, Math.min(5, cornerStars));
        if (stars > 0) {
            float outerR = Math.min(w, h) * 0.075f;
            float gap = outerR * 2.05f;
            float cy = y + outerR + 4f;
            float right = x + w - outerR - 4f;
            Color starColor = grayscale
                    ? new Color(0.45f, 0.45f, 0.45f, a)
                    : new Color(1f, 0.85f, 0.2f, a);
            ctx.shapes.begin(ShapeRenderer.ShapeType.Filled);
            for (int i = 0; i < stars; i++) {
                drawStar(ctx.shapes, right - (stars - 1 - i) * gap, cy, outerR, starColor);
            }
            ctx.shapes.end();
        }

    }

    private static void drawStar(ShapeRenderer shapes, float cx, float cy, float outerR, Color color) {
        float innerR = outerR * 0.4f;
        shapes.setColor(color);
        for (int i = 0; i < 5; i++) {
            double a0 = Math.PI / 2.0 + i * (2.0 * Math.PI / 5.0);
            double a1 = a0 + Math.PI / 5.0;
            double a2 = a0 + 2.0 * Math.PI / 5.0;
            float ox0 = cx + (float) (Math.cos(a0) * outerR);
            float oy0 = cy + (float) (Math.sin(a0) * outerR);
            float ix = cx + (float) (Math.cos(a1) * innerR);
            float iy = cy + (float) (Math.sin(a1) * innerR);
            float ox1 = cx + (float) (Math.cos(a2) * outerR);
            float oy1 = cy + (float) (Math.sin(a2) * outerR);
            shapes.triangle(cx, cy, ox0, oy0, ix, iy);
            shapes.triangle(cx, cy, ix, iy, ox1, oy1);
        }
    }
}
