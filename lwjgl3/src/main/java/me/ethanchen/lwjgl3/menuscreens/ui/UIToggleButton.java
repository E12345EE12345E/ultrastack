package me.ethanchen.lwjgl3.menuscreens.ui;

import com.badlogic.gdx.graphics.Color;

/**
 * Toggle-style button that stays highlighted while {@link #selected} is true.
 * Used for mutually-exclusive option groups (e.g. local-player input mode).
 */
public class UIToggleButton extends UIButton {
    public boolean selected;

    public UIToggleButton(double x, double y, double w, double h, String text, Runnable action) {
        super(x, y, w, h, text, action);
    }

    @Override
    protected boolean isActive() {
        return selected;
    }

    @Override
    protected boolean showBackgroundWhenHovered() {
        return true;
    }

    @Override
    protected Color activeBackgroundColor() {
        return new Color(0.08f, 0.22f, 0.40f, 1f);
    }

    @Override
    protected Color currentBorderColor() {
        if (selected) return Color.CYAN;
        return hovered ? Color.CYAN : Color.WHITE;
    }
}
