package me.ethanchen.lwjgl3.menuscreens.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;

public class UIKeybindButton extends UIButton {
    public static final int UNSET = -1;

    private boolean listening = false;
    private int     boundKey;

    public UIKeybindButton(double x, double y, double w, double h, int initialKey) {
        super(x, y, w, h, keyName(initialKey), null);
        this.boundKey = initialKey;
    }

    public int     getBoundKey()   { return boundKey; }
    public boolean isListening()   { return listening; }

    /** Cancels listening without changing the bound key. */
    public void cancelListening() {
        listening = false;
        text = keyName(boundKey);
    }

    /** Clears the bound key to {@link #UNSET}. */
    public void clearKey() {
        boundKey  = UNSET;
        listening = false;
        text      = keyName(UNSET);
    }

    /** Binds the given keycode and exits listening mode. */
    public void bindKey(int keycode) {
        boundKey  = keycode;
        listening = false;
        text      = keyName(keycode);
        glow      = 1.0f;
    }

    @Override
    public void onClick() {
        listening = true;
        text      = "Press any key...";
        glow      = 0f;
    }

    // -------------------------------------------------------------------------
    // Template-method overrides
    // -------------------------------------------------------------------------

    @Override protected boolean isActive()               { return listening; }
    @Override protected boolean suppressHover()          { return listening; }
    @Override protected boolean showBackgroundWhenHovered() { return true; }

    @Override
    protected Color activeBackgroundColor() { return new Color(0.12f, 0.10f, 0.02f, 1f); }

    @Override
    protected Color currentBorderColor() {
        if (listening) return Color.YELLOW;
        return hovered ? Color.CYAN : Color.WHITE;
    }

    @Override
    protected Color currentTextColor() { return listening ? Color.YELLOW : Color.WHITE; }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    static String keyName(int keycode) {
        if (keycode == UNSET) return "";
        String name = Input.Keys.toString(keycode);
        return (name != null) ? name : "Key " + keycode;
    }
}
