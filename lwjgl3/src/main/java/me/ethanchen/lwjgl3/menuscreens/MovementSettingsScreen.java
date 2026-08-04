package me.ethanchen.lwjgl3.menuscreens;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerAdapter;
import com.badlogic.gdx.controllers.Controllers;

import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.ui.UIButton;
import me.ethanchen.lwjgl3.menuscreens.ui.UIControllerBindButton;
import me.ethanchen.lwjgl3.menuscreens.ui.UIKeybindButton;
import me.ethanchen.lwjgl3.menuscreens.ui.UIText;
import me.ethanchen.lwjgl3.settings.GameSettings;
import me.ethanchen.lwjgl3.settings.SettingsManager;

public class MovementSettingsScreen extends MenuScreen {
    // Column centers (relative coords)
    private static final double LABEL_X  = 0.12;
    private static final double KEY1_X   = 0.32;
    private static final double KEY2_X   = 0.50;
    private static final double CTRL1_X  = 0.68;
    private static final double CTRL2_X  = 0.86;
    private static final double BTN_W    = 0.165;
    private static final double ROW_H    = 0.08;

    // Row Y positions and labels for the 9 actions
    private static final double[] ROW_Y = { 0.83, 0.75, 0.67, 0.59, 0.51, 0.43, 0.35, 0.27, 0.19 };
    private static final String[] ROW_LABELS = {
        "Move Left", "Move Right", "Soft Drop", "Hard Drop",
        "Rotate CW", "Rotate CCW", "Rotate 180", "Hold", "Ability"
    };
    private static final int ROW_COUNT = ROW_LABELS.length;

    // Metadata: how to read the initial value from MovementKeys for each action row
    private static final List<Function<GameSettings.MovementKeys, Integer>> KEY1_GETTERS = Arrays.asList(
        m -> m.left,  m -> m.right,  m -> m.softDrop,  m -> m.hardDrop,
        m -> m.rotateCw, m -> m.rotateCcw, m -> m.rotate180, m -> m.hold, m -> m.ability);
    private static final List<Function<GameSettings.MovementKeys, Integer>> KEY2_GETTERS = Arrays.asList(
        m -> m.left2, m -> m.right2, m -> m.softDrop2, m -> m.hardDrop2,
        m -> m.rotateCw2, m -> m.rotateCcw2, m -> m.rotate180_2, m -> m.hold2, m -> m.ability2);
    private static final List<Function<GameSettings.MovementKeys, Integer>> CTRL1_GETTERS = Arrays.asList(
        m -> m.ctrlLeft,  m -> m.ctrlRight,  m -> m.ctrlSoftDrop,  m -> m.ctrlHardDrop,
        m -> m.ctrlRotateCw, m -> m.ctrlRotateCcw, m -> m.ctrlRotate180, m -> m.ctrlHold, m -> m.ctrlAbility);
    private static final List<Function<GameSettings.MovementKeys, Integer>> CTRL2_GETTERS = Arrays.asList(
        m -> m.ctrlLeft2, m -> m.ctrlRight2, m -> m.ctrlSoftDrop2, m -> m.ctrlHardDrop2,
        m -> m.ctrlRotateCw2, m -> m.ctrlRotateCcw2, m -> m.ctrlRotate180_2, m -> m.ctrlHold2, m -> m.ctrlAbility2);

    // Metadata: how to write back to MovementKeys for each action row
    private static final List<BiConsumer<GameSettings.MovementKeys, Integer>> KEY1_SETTERS = Arrays.asList(
        (m, v) -> m.left = v,  (m, v) -> m.right = v,  (m, v) -> m.softDrop = v,  (m, v) -> m.hardDrop = v,
        (m, v) -> m.rotateCw = v, (m, v) -> m.rotateCcw = v, (m, v) -> m.rotate180 = v, (m, v) -> m.hold = v,
        (m, v) -> m.ability = v);
    private static final List<BiConsumer<GameSettings.MovementKeys, Integer>> KEY2_SETTERS = Arrays.asList(
        (m, v) -> m.left2 = v, (m, v) -> m.right2 = v, (m, v) -> m.softDrop2 = v, (m, v) -> m.hardDrop2 = v,
        (m, v) -> m.rotateCw2 = v, (m, v) -> m.rotateCcw2 = v, (m, v) -> m.rotate180_2 = v, (m, v) -> m.hold2 = v,
        (m, v) -> m.ability2 = v);
    private static final List<BiConsumer<GameSettings.MovementKeys, Integer>> CTRL1_SETTERS = Arrays.asList(
        (m, v) -> m.ctrlLeft = v,  (m, v) -> m.ctrlRight = v,  (m, v) -> m.ctrlSoftDrop = v,  (m, v) -> m.ctrlHardDrop = v,
        (m, v) -> m.ctrlRotateCw = v, (m, v) -> m.ctrlRotateCcw = v, (m, v) -> m.ctrlRotate180 = v, (m, v) -> m.ctrlHold = v,
        (m, v) -> m.ctrlAbility = v);
    private static final List<BiConsumer<GameSettings.MovementKeys, Integer>> CTRL2_SETTERS = Arrays.asList(
        (m, v) -> m.ctrlLeft2 = v, (m, v) -> m.ctrlRight2 = v, (m, v) -> m.ctrlSoftDrop2 = v, (m, v) -> m.ctrlHardDrop2 = v,
        (m, v) -> m.ctrlRotateCw2 = v, (m, v) -> m.ctrlRotateCcw2 = v, (m, v) -> m.ctrlRotate180_2 = v, (m, v) -> m.ctrlHold2 = v,
        (m, v) -> m.ctrlAbility2 = v);

    // Keyboard bind buttons (primary and secondary)
    private final UIKeybindButton[] key1Btns = new UIKeybindButton[ROW_COUNT];
    private final UIKeybindButton[] key2Btns = new UIKeybindButton[ROW_COUNT];

    // Controller bind buttons (slot 1 and slot 2)
    private final UIControllerBindButton[] ctrl1Btns = new UIControllerBindButton[ROW_COUNT];
    private final UIControllerBindButton[] ctrl2Btns = new UIControllerBindButton[ROW_COUNT];

    private final ControllerAdapter controllerListener = new ControllerAdapter() {
        @Override
        public boolean buttonDown(Controller controller, int buttonIndex) {
            UIControllerBindButton btn = getListeningCtrlButton();
            if (btn != null) {
                btn.bindButton(buttonIndex);
                return true;
            }
            return false;
        }
    };

    public MovementSettingsScreen(ClientApp app) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());
        GameSettings.MovementKeys keys = app.getSettings().movement;

        elements.add(new UIText(0.5, 0.94, "Movement Settings", 3));

        // Column headers
        elements.add(new UIText(KEY1_X,                   0.89, "Key 1",      0.85));
        elements.add(new UIText(KEY2_X,                   0.89, "Key 2",      0.85));
        elements.add(new UIText((CTRL1_X + CTRL2_X) / 2, 0.92, "Controller", 0.85));
        elements.add(new UIText(CTRL1_X,                  0.89, "Btn 1",      0.85));
        elements.add(new UIText(CTRL2_X,                  0.89, "Btn 2",      0.85));

        for (int i = 0; i < ROW_COUNT; i++) {
            elements.add(new UIText(LABEL_X, ROW_Y[i], ROW_LABELS[i], 0.85));

            key1Btns[i]  = new UIKeybindButton(KEY1_X,  ROW_Y[i], BTN_W, ROW_H, KEY1_GETTERS.get(i).apply(keys));
            key2Btns[i]  = new UIKeybindButton(KEY2_X,  ROW_Y[i], BTN_W, ROW_H, KEY2_GETTERS.get(i).apply(keys));
            ctrl1Btns[i] = new UIControllerBindButton(CTRL1_X, ROW_Y[i], BTN_W, ROW_H, CTRL1_GETTERS.get(i).apply(keys));
            ctrl2Btns[i] = new UIControllerBindButton(CTRL2_X, ROW_Y[i], BTN_W, ROW_H, CTRL2_GETTERS.get(i).apply(keys));

            elements.add(key1Btns[i]);
            elements.add(key2Btns[i]);
            elements.add(ctrl1Btns[i]);
            elements.add(ctrl2Btns[i]);
        }

        elements.add(new UIButton(0.22, 0.13, 0.20, ROW_H, "Remove Key", this::removeSelectedKey));
        elements.add(new UIButton(0.50, 0.13, 0.28, ROW_H, "Done", this::saveAndExit));

        Controllers.addListener(controllerListener);
    }

    @Override
    public boolean keyDown(int keycode) {
        UIKeybindButton listeningKey = getListeningKeyButton();
        if (listeningKey != null) {
            if (keycode == Input.Keys.ESCAPE) {
                listeningKey.cancelListening();
            } else {
                listeningKey.bindKey(keycode);
            }
            return true;
        }

        UIControllerBindButton listeningCtrl = getListeningCtrlButton();
        if (listeningCtrl != null) {
            if (keycode == Input.Keys.ESCAPE) {
                listeningCtrl.cancelListening();
            }
            return true;
        }

        // ESC while hovering a button clears its binding
        if (keycode == Input.Keys.ESCAPE) {
            for (int i = 0; i < ROW_COUNT; i++) {
                if (key1Btns[i].hovered)  { key1Btns[i].clearKey();     return true; }
                if (key2Btns[i].hovered)  { key2Btns[i].clearKey();     return true; }
                if (ctrl1Btns[i].hovered) { ctrl1Btns[i].clearButton(); return true; }
                if (ctrl2Btns[i].hovered) { ctrl2Btns[i].clearButton(); return true; }
            }
        }

        return super.keyDown(keycode);
    }

    @Override
    protected void onEscPressed() {
        Controllers.removeListener(controllerListener);
        app.switchMenu(new MainSettingsScreen(app));
    }

    @Override
    public void dispose() {
        Controllers.removeListener(controllerListener);
    }

    @Override
    public void update() {}

    private UIKeybindButton getListeningKeyButton() {
        for (int i = 0; i < ROW_COUNT; i++) {
            if (key1Btns[i].isListening()) return key1Btns[i];
            if (key2Btns[i].isListening()) return key2Btns[i];
        }
        return null;
    }

    private UIControllerBindButton getListeningCtrlButton() {
        for (int i = 0; i < ROW_COUNT; i++) {
            if (ctrl1Btns[i].isListening()) return ctrl1Btns[i];
            if (ctrl2Btns[i].isListening()) return ctrl2Btns[i];
        }
        return null;
    }

    private void removeSelectedKey() {
        UIKeybindButton listeningKey = getListeningKeyButton();
        if (listeningKey != null) {
            listeningKey.clearKey();
            return;
        }
        UIControllerBindButton listeningCtrl = getListeningCtrlButton();
        if (listeningCtrl != null) {
            listeningCtrl.clearButton();
            return;
        }
        for (int i = 0; i < ROW_COUNT; i++) {
            if (key1Btns[i].hovered)  { key1Btns[i].clearKey();     return; }
            if (key2Btns[i].hovered)  { key2Btns[i].clearKey();     return; }
            if (ctrl1Btns[i].hovered) { ctrl1Btns[i].clearButton(); return; }
            if (ctrl2Btns[i].hovered) { ctrl2Btns[i].clearButton(); return; }
        }
    }

    private void saveAndExit() {
        Controllers.removeListener(controllerListener);
        GameSettings settings = app.getSettings();
        GameSettings.MovementKeys m = settings.movement;
        for (int i = 0; i < ROW_COUNT; i++) {
            KEY1_SETTERS.get(i).accept(m, key1Btns[i].getBoundKey());
            KEY2_SETTERS.get(i).accept(m, key2Btns[i].getBoundKey());
            CTRL1_SETTERS.get(i).accept(m, ctrl1Btns[i].getBoundButton());
            CTRL2_SETTERS.get(i).accept(m, ctrl2Btns[i].getBoundButton());
        }
        SettingsManager.save(settings);
        app.switchMenu(new MainSettingsScreen(app));
    }
}
