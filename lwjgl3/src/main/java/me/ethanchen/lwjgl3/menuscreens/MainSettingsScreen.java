package me.ethanchen.lwjgl3.menuscreens;

import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.ui.UIButton;
import me.ethanchen.lwjgl3.menuscreens.ui.UIText;

/**
 * Unused hub. Settings are opened from decorated menus via {@link SettingsHub}; this screen is
 * kept so the old UI remains in the tree but is not reachable from any live menu.
 */
public class MainSettingsScreen extends MenuScreen {

    public MainSettingsScreen(ClientApp app) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());

        elements.add(new UIText(0.5, 0.88, "Settings", 3));
        elements.add(new UIButton(0.5, 0.70, 0.5, 0.1, "Movement Settings",
                () -> app.switchMenu(new MovementSettingsScreen(app, () -> new MainMenu(app)))));
        elements.add(new UIButton(0.5, 0.55, 0.5, 0.1, "Color Settings",
                () -> app.switchMenu(new ColorSettingsScreen(app, () -> new MainMenu(app)))));
        elements.add(new UIButton(0.5, 0.40, 0.5, 0.1, "Sound Settings",
                () -> app.switchMenu(new SoundSettingsScreen(app, () -> new MainMenu(app)))));
        elements.add(new UIButton(0.5, 0.20, 0.3, 0.08, "Back",
                () -> app.switchMenu(new MainMenu(app))));
    }

    @Override
    protected void onEscPressed() {
        app.switchMenu(new MainMenu(app));
    }

    @Override
    public void update() {}

}
