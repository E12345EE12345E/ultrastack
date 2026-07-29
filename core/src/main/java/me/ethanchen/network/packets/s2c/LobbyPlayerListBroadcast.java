package me.ethanchen.network.packets.s2c;

import me.ethanchen.network.packets.NetworkPacket;

public class LobbyPlayerListBroadcast extends NetworkPacket {
    /** Active players in slot order. */
    public String[] playerNames = new String[0];
    /** Spectators in join order (display names, including "Name - 2" extras). */
    public String[] spectatorNames = new String[0];
}
