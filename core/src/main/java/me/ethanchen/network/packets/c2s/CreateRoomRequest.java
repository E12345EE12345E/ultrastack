package me.ethanchen.network.packets.c2s;

import me.ethanchen.network.packets.NetworkPacket;

/**
 * Requests creation of a new room. The room's game mode is selected later via
 * {@link me.ethanchen.network.packets.c2s.StartGameRequest}, so this packet carries no payload.
 */
public class CreateRoomRequest extends NetworkPacket {
}
