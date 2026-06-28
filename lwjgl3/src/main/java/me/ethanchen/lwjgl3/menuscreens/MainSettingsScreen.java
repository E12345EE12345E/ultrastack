package me.ethanchen.lwjgl3.menuscreens;

import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.ui.UIButton;
import me.ethanchen.lwjgl3.menuscreens.ui.UIText;

public class MainSettingsScreen extends MenuScreen {

    public MainSettingsScreen(ClientApp app) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());

        elements.add(new UIText(0.5, 0.88, "Settings", 3));
        elements.add(new UIButton(0.5, 0.70, 0.5, 0.1, "Movement Settings",
                () -> app.switchMenu(new MovementSettingsScreen(app))));
        elements.add(new UIButton(0.5, 0.55, 0.5, 0.1, "Color Settings",
                () -> app.switchMenu(new ColorSettingsScreen(app))));
        elements.add(new UIButton(0.5, 0.40, 0.5, 0.1, "Sound Settings",
                () -> app.switchMenu(new SoundSettingsScreen(app))));
        elements.add(new UIButton(0.5, 0.20, 0.3, 0.08, "Back",
                () -> app.switchMenu(new MainMenu(app))));
    }

    @Override
    protected void onEscPressed() {
        app.switchMenu(new MainMenu(app));
    }

    @Override
    public void update() {}

    @Override
    public void render() {
        elements.forEach(el -> el.render(shapes, sprites, font));
    }
}
