package me.ethanchen.network.packets.c2s;

import me.ethanchen.game.GameMode;
import me.ethanchen.network.packets.NetworkPacket;

public class StartGameRequest extends NetworkPacket {
    public GameMode gamemode;
}
