package me.ethanchen.lwjgl3.menuscreens.ui;

import com.badlogic.gdx.graphics.Texture;

/**
 * Design-pixel helpers for Aspect-locked UI laid out against a 1920×1080 (16:9) canvas.
 * Relative coordinates passed to {@link UIElement} are {@code px / DESIGN_W} and {@code py / DESIGN_H}.
 */
public final class DesignUi {
    public static final float DESIGN_W = 1920f;
    public static final float DESIGN_H = 1080f;

    private DesignUi() {}

    public static double nx(double designX) { return designX / DESIGN_W; }
    public static double ny(double designY) { return designY / DESIGN_H; }
    public static double nw(double designW) { return designW / DESIGN_W; }
    public static double nh(double designH) { return designH / DESIGN_H; }

    /** Square {@link UIInventoryButton} sized in design pixels. */
    public static UIInventoryButton inventoryButton(double centerXPx, double centerYPx, double sizePx,
                                                    Texture icon, Runnable action) {
        return UIInventoryButton.design(centerXPx, centerYPx, sizePx, icon, action);
    }
}
