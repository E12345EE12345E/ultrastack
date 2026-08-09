package me.ethanchen.lwjgl3.settings;

import me.ethanchen.game.GameMode;

/**
 * Pending lobby/game-start configuration, held once per client instance (see
 * {@link me.ethanchen.lwjgl3.ClientApp#getLobbySettings()}) so it survives across screens and
 * across games within the same running client. Host changes are sent as
 * {@code LobbySettingsRequest}; all clients apply {@code LobbySettingsBroadcast}. Add future
 * lobby options here (e.g. max players, starting level) — {@code LobbySettingsScreen} builds
 * one control per field the same way.
 */
public class LobbySettings {
    public GameMode gamemode = GameMode.MULTIPLAYER_SCORE;
}
