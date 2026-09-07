package me.ethanchen.lwjgl3.menuscreens;

import java.util.function.Supplier;

import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedButton;
import me.ethanchen.lwjgl3.menuscreens.decorated.DecoratedText;
import me.ethanchen.lwjgl3.menuscreens.decorated.Widget;

/**
 * Shared Movement / Color / Sound picker used by decorated menus. Back dismisses the widget;
 * each category switches to the existing (non-decorated) settings screen and returns via
 * {@code returnTo}.
 */
public final class SettingsHub {
    private SettingsHub() {}

    public static Widget createWidget(ClientApp app, DecoratedMenuScreen host,
                                      Supplier<MenuScreen> returnTo) {
        Widget w = new Widget(960f, 520f, 580f, 580f);
        w.add(new DecoratedText(0f, 0f, "Settings", 2.2f), 0f, 228f);
        DecoratedButton movement = new DecoratedButton(0f, 0f, 440f, 78f, "Movement",
                () -> app.switchMenu(new MovementSettingsScreen(app, returnTo)));
        DecoratedButton color = new DecoratedButton(0f, 0f, 440f, 78f, "Color",
                () -> app.switchMenu(new ColorSettingsScreen(app, returnTo)));
        DecoratedButton sound = new DecoratedButton(0f, 0f, 440f, 78f, "Sound",
                () -> app.switchMenu(new SoundSettingsScreen(app, returnTo)));
        DecoratedButton back = new DecoratedButton(0f, 0f, 300f, 68f, "Back", host::closeTopWidget);
        movement.fontSize = 1.55f;
        color.fontSize = 1.55f;
        sound.fontSize = 1.55f;
        back.fontSize = 1.4f;

        float btnH = 78f;
        float backH = 68f;
        float gap = 32f;
        float movementY = 118f;
        float colorY = movementY - btnH - gap;
        float soundY = colorY - btnH - gap;
        float backY = soundY - btnH * 0.5f - gap - backH * 0.5f;
        w.add(movement, 0f, movementY);
        w.add(color, 0f, colorY);
        w.add(sound, 0f, soundY);
        w.add(back, 0f, backY);
        return w;
    }
}
