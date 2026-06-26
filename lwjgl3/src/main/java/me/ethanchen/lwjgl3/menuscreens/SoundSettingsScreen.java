package me.ethanchen.lwjgl3.menuscreens;

import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.ui.UIButton;
import me.ethanchen.lwjgl3.menuscreens.ui.UISlider;
import me.ethanchen.lwjgl3.menuscreens.ui.UIText;
import me.ethanchen.lwjgl3.music.AudioManager;
import me.ethanchen.lwjgl3.settings.GameSettings;
import me.ethanchen.lwjgl3.settings.SettingsManager;

/**
 * Settings screen for audio volume controls.
 * Changes are applied to {@link AudioManager} live (so music volume is heard immediately)
 * but only committed to disk when the Done button is pressed.
 * Pressing Escape reverts the AudioManager to the last saved settings.
 */
public class SoundSettingsScreen extends MenuScreen {

    private static final double SLIDER_X = 0.5;
    private static final double SLIDER_W = 0.55;
    private static final double SLIDER_H = 0.08;

    private final UISlider masterSlider;
    private final UISlider sfxSlider;
    private final UISlider musicSlider;

    public SoundSettingsScreen(ClientApp app) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());

        GameSettings.VolumeSettings vol = app.getSettings().volume;

        elements.add(new UIText(0.5, 0.90, "Sound Settings", 3));

        elements.add(new UIText(0.5, 0.75, "Master Volume", 1.2));
        masterSlider = new UISlider(SLIDER_X, 0.66, SLIDER_W, SLIDER_H, "MASTER", 0, 100, vol.master);
        masterSlider.setOnChange(this::onVolumeChanged);
        elements.add(masterSlider);

        elements.add(new UIText(0.5, 0.54, "SFX Volume", 1.2));
        sfxSlider = new UISlider(SLIDER_X, 0.45, SLIDER_W, SLIDER_H, "SFX", 0, 100, vol.sfx);
        sfxSlider.setOnChange(this::onVolumeChanged);
        elements.add(sfxSlider);

        elements.add(new UIText(0.5, 0.33, "Music Volume", 1.2));
        musicSlider = new UISlider(SLIDER_X, 0.24, SLIDER_W, SLIDER_H, "MUSIC", 0, 100, vol.music);
        musicSlider.setOnChange(this::onVolumeChanged);
        elements.add(musicSlider);

        elements.add(new UIButton(0.5, 0.10, 0.28, 0.08, "Done", this::saveAndExit));
    }

    /** Applies current slider values to AudioManager live so changes are heard immediately. */
    private void onVolumeChanged() {
        GameSettings.VolumeSettings temp = new GameSettings.VolumeSettings();
        temp.master = masterSlider.getValue();
        temp.sfx    = sfxSlider.getValue();
        temp.music  = musicSlider.getValue();
        AudioManager.getInstance().setVolumeSettings(temp);
    }

    private void saveAndExit() {
        GameSettings.VolumeSettings vol = app.getSettings().volume;
        vol.master = masterSlider.getValue();
        vol.sfx    = sfxSlider.getValue();
        vol.music  = musicSlider.getValue();
        AudioManager.getInstance().setVolumeSettings(vol);
        SettingsManager.save(app.getSettings());
        app.switchMenu(new MainSettingsScreen(app));
    }

    @Override
    protected void onEscPressed() {
        AudioManager.getInstance().setVolumeSettings(app.getSettings().volume);
        app.switchMenu(new MainSettingsScreen(app));
    }

    @Override
    public void update() {}

    @Override
    public void render() {
        elements.forEach(el -> el.render(shapes, sprites, font));
    }
}
