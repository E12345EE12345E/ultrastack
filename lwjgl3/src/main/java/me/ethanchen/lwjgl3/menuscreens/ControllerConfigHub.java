package me.ethanchen.lwjgl3.menuscreens;

import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.input.LocalPlayerMode;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedButton;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedText;
import me.ethanchen.lwjgl3.menuscreens.decorated.Widget;

/**
 * Shared controller-mode overlay. Mode toggles write {@link ClientApp#setLocalPlayerMode};
 * {@code onChange} is optional so a lobby can sync seat count while the room browser no-ops.
 */
public final class ControllerConfigHub {
    public final Widget widget;

    private static final LocalPlayerMode[] MODES = {
            LocalPlayerMode.KEYBOARD_OR_CONTROLLER,
            LocalPlayerMode.KEYBOARD_PLUS_CONTROLLERS,
            LocalPlayerMode.CONTROLLERS_ONLY
    };

    private final ClientApp app;
    private final Runnable onChange;
    private final DecoratedButton[] modeButtons = new DecoratedButton[MODES.length];
    private final DecoratedText controllerCountText;
    private int lastControllerCount = -1;
    /** When false (PvE), mode toggles stay visible but do not change the roster. */
    private boolean enabled = true;

    private ControllerConfigHub(Widget widget, ClientApp app, Runnable onChange,
                                DecoratedText controllerCountText) {
        this.widget = widget;
        this.app = app;
        this.onChange = onChange != null ? onChange : () -> {};
        this.controllerCountText = controllerCountText;
    }

    public static ControllerConfigHub create(ClientApp app, DecoratedMenuScreen host, Runnable onChange) {
        Widget w = new Widget(960f, 520f, 580f, 640f);
        w.add(new DecoratedText(0f, 0f, "Controller", 2.2f), 0f, 250f);

        DecoratedText countText = new DecoratedText(0f, 0f, controllerLabel(0), 1.45f);
        ControllerConfigHub hub = new ControllerConfigHub(w, app, onChange, countText);

        float btnH = 78f;
        float backH = 68f;
        float gap = 24f;
        float mode0Y = 150f;
        for (int i = 0; i < MODES.length; i++) {
            final LocalPlayerMode mode = MODES[i];
            DecoratedButton btn = new DecoratedButton(0f, 0f, 440f, btnH, mode.label(),
                    () -> hub.selectMode(mode));
            btn.fontSize = 1.55f;
            btn.info(mode.description());
            hub.modeButtons[i] = btn;
            w.add(btn, 0f, mode0Y - i * (btnH + gap));
        }

        float mode2Y = mode0Y - 2 * (btnH + gap);
        float countY = mode2Y - btnH * 0.5f - gap - 16f;
        float backY = countY - 48f - backH * 0.5f;
        w.add(countText, 0f, countY);

        DecoratedButton back = new DecoratedButton(0f, 0f, 300f, backH, "Back", host::closeTopWidget);
        back.fontSize = 1.4f;
        w.add(back, 0f, backY);

        hub.refreshSelected();
        hub.refreshControllerCount(false);
        return hub;
    }

    /**
     * Enables or disables the local-player mode toggles. Disabled for PvE (single local player
     * only); the buttons stay visible but {@link #selectMode} / {@link #tick()} no-op so the
     * server roster is not bumped above 1.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Refresh count text and selected chrome; fires {@code onChange} when the count changes. */
    public void tick() {
        refreshSelected();
        refreshControllerCount(enabled);
    }

    private void selectMode(LocalPlayerMode mode) {
        if (!enabled) return;
        app.setLocalPlayerMode(mode);
        refreshSelected();
        onChange.run();
    }

    private void refreshSelected() {
        LocalPlayerMode current = app.getLocalPlayerMode();
        for (int i = 0; i < MODES.length; i++) {
            modeButtons[i].selected = (MODES[i] == current);
        }
    }

    private void refreshControllerCount(boolean fireOnChange) {
        int count = app.getControllerRoster().getConnectedCount();
        controllerCountText.text = controllerLabel(count);
        if (fireOnChange && count != lastControllerCount && lastControllerCount >= 0) {
            onChange.run();
        }
        lastControllerCount = count;
    }

    private static String controllerLabel(int count) {
        return "Controllers: " + count;
    }
}
