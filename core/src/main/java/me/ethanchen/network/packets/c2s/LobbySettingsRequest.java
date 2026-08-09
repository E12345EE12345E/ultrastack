package me.ethanchen.network.packets.c2s;

import me.ethanchen.game.GameMode;
import me.ethanchen.network.packets.NetworkPacket;

/**
 * Host-only: updates the room's pending lobby settings (currently just gamemode) so peers can
 * reflect character-loadout enablement before {@link StartGameRequest}.
 */
public class LobbySettingsRequest extends NetworkPacket {
    public GameMode gamemode;
}
