package me.ethanchen.lwjgl3.menuscreens.ui;

import com.badlogic.gdx.graphics.Color;

public class UIControllerBindButton extends UIButton {
    private static final int UNBOUND = -1;

    private boolean listening   = false;
    private int     boundButton;

    public UIControllerBindButton(double x, double y, double w, double h, int initialButton) {
        super(x, y, w, h, buttonName(initialButton), null);
        this.boundButton = initialButton;
    }

    public int     getBoundButton() { return boundButton; }
    public boolean isListening()    { return listening; }

    public void cancelListening() {
        listening = false;
        text      = buttonName(boundButton);
    }

    /** Clears the bound button to unbound (-1). */
    public void clearButton() {
        boundButton = UNBOUND;
        listening   = false;
        text        = buttonName(UNBOUND);
    }

    public void bindButton(int buttonIndex) {
        boundButton = buttonIndex;
        listening   = false;
        text        = buttonName(buttonIndex);
        glow        = 1.0f;
    }

    @Override
    public void onClick() {
        listening = true;
        text      = "Press button...";
        glow      = 0f;
    }

    // -------------------------------------------------------------------------
    // Template-method overrides
    // -------------------------------------------------------------------------

    @Override protected boolean isActive()               { return listening; }
    @Override protected boolean suppressHover()          { return listening; }
    @Override protected boolean showBackgroundWhenHovered() { return true; }

    @Override
    protected Color idleBackgroundColor()   { return new Color(0.10f, 0.15f, 0.20f, 1f); }

    @Override
    protected Color activeBackgroundColor() { return new Color(0.02f, 0.10f, 0.12f, 1f); }

    @Override
    protected Color currentBorderColor() {
        return (listening || hovered) ? Color.CYAN : new Color(0.5f, 0.7f, 1.0f, 1f);
    }

    @Override
    protected Color currentTextColor() { return listening ? Color.CYAN : Color.WHITE; }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    public static String buttonName(int idx) {
        switch (idx) {
            case UNBOUND: return "";
            // SDL2 GameController button order (gdx-controllers 2.x)
            case 0:  return "A";
            case 1:  return "B";
            case 2:  return "X";
            case 3:  return "Y";
            case 4:  return "Back";
            case 5:  return "Guide";
            case 6:  return "Start";
            case 7:  return "LS";
            case 8:  return "RS";
            case 9:  return "LB";
            case 10: return "RB";
            case 11: return "D\u2191";
            case 12: return "D\u2193";
            case 13: return "D\u2190";
            case 14: return "D\u2192";
            default: return "Btn " + idx;
        }
    }
}
