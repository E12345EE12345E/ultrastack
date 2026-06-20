package me.ethanchen.lwjgl3.menuscreens;

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

    // Row Y positions (8 actions)
    private static final double[] ROW_Y = { 0.83, 0.75, 0.67, 0.59, 0.51, 0.43, 0.35, 0.27 };
    private static final String[] ROW_LABELS = {
        "Move Left", "Move Right", "Soft Drop", "Hard Drop",
        "Rotate CW", "Rotate CCW", "Rotate 180", "Hold"
    };

    // Keyboard bind buttons (primary and secondary)
    private final UIKeybindButton[] key1Btns = new UIKeybindButton[8];
    private final UIKeybindButton[] key2Btns = new UIKeybindButton[8];

    // Controller bind buttons (slot 1 and slot 2)
    private final UIControllerBindButton[] ctrl1Btns = new UIControllerBindButton[8];
    private final UIControllerBindButton[] ctrl2Btns = new UIControllerBindButton[8];

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
        elements.add(new UIText(KEY1_X,              0.89, "Key 1",      0.85));
        elements.add(new UIText(KEY2_X,              0.89, "Key 2",      0.85));
        elements.add(new UIText((CTRL1_X + CTRL2_X) / 2, 0.92, "Controller", 0.85));
        elements.add(new UIText(CTRL1_X,             0.89, "Btn 1",      0.85));
        elements.add(new UIText(CTRL2_X,             0.89, "Btn 2",      0.85));

        // Per-action primary keyboard defaults
        int[] key1Defaults = {
            keys.left, keys.right, keys.softDrop, keys.hardDrop,
            keys.rotateCw, keys.rotateCcw, keys.rotate180, keys.hold
        };
        // Per-action secondary keyboard defaults
        int[] key2Defaults = {
            keys.left2, keys.right2, keys.softDrop2, keys.hardDrop2,
            keys.rotateCw2, keys.rotateCcw2, keys.rotate180_2, keys.hold2
        };
        // Controller slot 1 defaults
        int[] ctrl1Defaults = {
            keys.ctrlLeft, keys.ctrlRight, keys.ctrlSoftDrop, keys.ctrlHardDrop,
            keys.ctrlRotateCw, keys.ctrlRotateCcw, keys.ctrlRotate180, keys.ctrlHold
        };
        // Controller slot 2 defaults
        int[] ctrl2Defaults = {
            keys.ctrlLeft2, keys.ctrlRight2, keys.ctrlSoftDrop2, keys.ctrlHardDrop2,
            keys.ctrlRotateCw2, keys.ctrlRotateCcw2, keys.ctrlRotate180_2, keys.ctrlHold2
        };

        for (int i = 0; i < 8; i++) {
            elements.add(new UIText(LABEL_X, ROW_Y[i], ROW_LABELS[i], 0.85));

            key1Btns[i]  = new UIKeybindButton(KEY1_X,  ROW_Y[i], BTN_W, ROW_H, key1Defaults[i]);
            key2Btns[i]  = new UIKeybindButton(KEY2_X,  ROW_Y[i], BTN_W, ROW_H, key2Defaults[i]);
            ctrl1Btns[i] = new UIControllerBindButton(CTRL1_X, ROW_Y[i], BTN_W, ROW_H, ctrl1Defaults[i]);
            ctrl2Btns[i] = new UIControllerBindButton(CTRL2_X, ROW_Y[i], BTN_W, ROW_H, ctrl2Defaults[i]);

            elements.add(key1Btns[i]);
            elements.add(key2Btns[i]);
            elements.add(ctrl1Btns[i]);
            elements.add(ctrl2Btns[i]);
        }

        elements.add(new UIButton(0.5, 0.13, 0.28, ROW_H, "Done", this::saveAndExit));

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
            for (int i = 0; i < 8; i++) {
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

    @Override
    public void render() {
        elements.forEach(element -> element.render(shapes, sprites, font));
    }

    private UIKeybindButton getListeningKeyButton() {
        for (int i = 0; i < 8; i++) {
            if (key1Btns[i].isListening()) return key1Btns[i];
            if (key2Btns[i].isListening()) return key2Btns[i];
        }
        return null;
    }

    private UIControllerBindButton getListeningCtrlButton() {
        for (int i = 0; i < 8; i++) {
            if (ctrl1Btns[i].isListening()) return ctrl1Btns[i];
            if (ctrl2Btns[i].isListening()) return ctrl2Btns[i];
        }
        return null;
    }

    private void saveAndExit() {
        Controllers.removeListener(controllerListener);
        GameSettings settings = app.getSettings();
        GameSettings.MovementKeys m = settings.movement;

        m.left       = key1Btns[0].getBoundKey();
        m.right      = key1Btns[1].getBoundKey();
        m.softDrop   = key1Btns[2].getBoundKey();
        m.hardDrop   = key1Btns[3].getBoundKey();
        m.rotateCw   = key1Btns[4].getBoundKey();
        m.rotateCcw  = key1Btns[5].getBoundKey();
        m.rotate180  = key1Btns[6].getBoundKey();
        m.hold       = key1Btns[7].getBoundKey();

        m.left2       = key2Btns[0].getBoundKey();
        m.right2      = key2Btns[1].getBoundKey();
        m.softDrop2   = key2Btns[2].getBoundKey();
        m.hardDrop2   = key2Btns[3].getBoundKey();
        m.rotateCw2   = key2Btns[4].getBoundKey();
        m.rotateCcw2  = key2Btns[5].getBoundKey();
        m.rotate180_2 = key2Btns[6].getBoundKey();
        m.hold2       = key2Btns[7].getBoundKey();

        m.ctrlLeft      = ctrl1Btns[0].getBoundButton();
        m.ctrlRight     = ctrl1Btns[1].getBoundButton();
        m.ctrlSoftDrop  = ctrl1Btns[2].getBoundButton();
        m.ctrlHardDrop  = ctrl1Btns[3].getBoundButton();
        m.ctrlRotateCw  = ctrl1Btns[4].getBoundButton();
        m.ctrlRotateCcw = ctrl1Btns[5].getBoundButton();
        m.ctrlRotate180 = ctrl1Btns[6].getBoundButton();
        m.ctrlHold      = ctrl1Btns[7].getBoundButton();

        m.ctrlLeft2      = ctrl2Btns[0].getBoundButton();
        m.ctrlRight2     = ctrl2Btns[1].getBoundButton();
        m.ctrlSoftDrop2  = ctrl2Btns[2].getBoundButton();
        m.ctrlHardDrop2  = ctrl2Btns[3].getBoundButton();
        m.ctrlRotateCw2  = ctrl2Btns[4].getBoundButton();
        m.ctrlRotateCcw2 = ctrl2Btns[5].getBoundButton();
        m.ctrlRotate180_2 = ctrl2Btns[6].getBoundButton();
        m.ctrlHold2      = ctrl2Btns[7].getBoundButton();

        SettingsManager.save(settings);
        app.switchMenu(new MainSettingsScreen(app));
    }
}
