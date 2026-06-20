package me.ethanchen.lwjgl3.settings;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;

public class SettingsManager {
    private static final String SETTINGS_FILE = "settings.json";

    public static GameSettings load() {
        try {
            FileHandle file = Gdx.files.local(SETTINGS_FILE);
            if (file.exists()) {
                Json json = new Json();
                return json.fromJson(GameSettings.class, file.readString());
            }
        } catch (Exception e) {
            System.err.println("Failed to load settings, using defaults: " + e.getMessage());
        }
        return new GameSettings();
    }

    public static void save(GameSettings settings) {
        try {
            FileHandle file = Gdx.files.local(SETTINGS_FILE);
            Json json = new Json();
            json.setOutputType(JsonWriter.OutputType.json);
            file.writeString(json.prettyPrint(settings), false);
        } catch (Exception e) {
            System.err.println("Failed to save settings: " + e.getMessage());
        }
    }
}
