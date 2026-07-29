package me.ethanchen.network.packets.c2s;

import me.ethanchen.network.packets.NetworkPacket;

/**
 * Requests creation of a new room. The room's game mode is selected later via
 * {@link me.ethanchen.network.packets.c2s.StartGameRequest}.
 */
public class CreateRoomRequest extends NetworkPacket {
    /** Number of local players this client wants seated (keyboard/controllers). */
    public byte localPlayers = 1;
}
