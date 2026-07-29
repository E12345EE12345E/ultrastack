package me.ethanchen.lwjgl3.menuscreens;

import java.util.ArrayList;

import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.input.LocalPlayerMode;
import me.ethanchen.lwjgl3.menuscreens.ui.TextInput;
import me.ethanchen.lwjgl3.menuscreens.ui.UIElement;
import me.ethanchen.lwjgl3.menuscreens.ui.UIText;
import me.ethanchen.lwjgl3.menuscreens.ui.UIToggleButton;

/**
 * Right-side sidebar with local-player mode toggles ("K | C", "K + C", "C") and a
 * connected-controller count digit. Shared by lobby and room-browser screens.
 */
public class LocalPlayerSidebar {

    private final ClientApp app;
    private final Runnable onChange;
    private final UIToggleButton[] modeButtons = new UIToggleButton[3];
    private final TextInput controllerCountText;
    private int lastControllerCount = -1;

    private static final LocalPlayerMode[] MODES = {
            LocalPlayerMode.KEYBOARD_OR_CONTROLLER,
            LocalPlayerMode.KEYBOARD_PLUS_CONTROLLERS,
            LocalPlayerMode.CONTROLLERS_ONLY
    };

    public LocalPlayerSidebar(ClientApp app, ArrayList<UIElement> elements, Runnable onChange) {
        this.app = app;
        this.onChange = onChange != null ? onChange : () -> {};
        this.controllerCountText = new TextInput();

        double x = 0.92;
        double w = 0.13;
        double h = 0.07;
        double[] ys = { 0.72, 0.62, 0.52 };

        for (int i = 0; i < MODES.length; i++) {
            final LocalPlayerMode mode = MODES[i];
            UIToggleButton btn = new UIToggleButton(x, ys[i], w, h, mode.label(), null);
            btn.selected = (app.getLocalPlayerMode() == mode);
            btn.action = () -> selectMode(mode);
            modeButtons[i] = btn;
            elements.add(btn);
        }

        elements.add(new UIText(x, 0.42, "Controllers", 0.85));
        elements.add(new UIText(x, 0.36, controllerCountText, 1.5));
        refreshControllerCount(false);
    }

    private void selectMode(LocalPlayerMode mode) {
        app.setLocalPlayerMode(mode);
        for (int i = 0; i < MODES.length; i++) {
            modeButtons[i].selected = (MODES[i] == mode);
        }
        onChange.run();
    }

    /** Refresh the controller digit; fires {@code onChange} when the count changes. */
    public void tick() {
        refreshControllerCount(true);
    }

    private void refreshControllerCount(boolean fireOnChange) {
        int count = app.getControllerRoster().getConnectedCount();
        controllerCountText.set(String.valueOf(count));
        if (fireOnChange && count != lastControllerCount && lastControllerCount >= 0) {
            onChange.run();
        }
        lastControllerCount = count;
    }
}
