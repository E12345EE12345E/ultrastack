package me.ethanchen.lwjgl3.menuscreens;

import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.ui.*;

public class MainMenu extends MenuScreen {
    public MainMenu(ClientApp app) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());
        elements.add(new UIText(0.5, 0.8, "Tetris UltraStack", 4));
        elements.add(new UIButton(0.5, 0.6, 0.5, 0.1, "Multiplayer", () -> app.switchMenu(new MultiplayerMenu(app))));
        elements.add(new UIButton(0.5, 0.45, 0.5, 0.1, "test", () -> app.switchMenu(new TestMenu(app))));
        elements.add(new UIButton(0.5, 0.3, 0.5, 0.1, "Settings", () -> app.switchMenu(new MainSettingsScreen(app))));
        //elements.add(new UITextBox(0.5, 0.45, 0.4, 0.08));
        //elements.add(new UIButton(0.5, 0.3, 0.3, 0.08, "Reconnect", () -> app.tryConnect()));
    }

    @Override
    public void update() {
        
    }

    @Override
    public void render() {
        elements.forEach(element -> {
            element.render(shapes, sprites, font);
        });
    }
}
