package me.ethanchen.lwjgl3.menuscreens;

import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.ui.*;
import me.ethanchen.network.NetConfig;

public class MainMenu extends MenuScreen {
    public MainMenu(ClientApp app) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());
        elements.add(new UIText(0.5, 0.8, "Tetris UltraStack", 4));
        elements.add(new UIButton(0.5, 0.6, 0.5, 0.1, "Multiplayer", () -> {
            app.setLanMode(false);
            app.setConnectDestination(NetConfig.DEFAULT_SERVER_HOST, NetConfig.PORT);
            app.tryConnectAuto();
            app.switchMenu(new ServerConnectMenu(app, true));
        }));
        elements.add(new UIButton(0.5, 0.45, 0.5, 0.1, "LAN", () -> app.switchMenu(new LanMenu(app))));
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
