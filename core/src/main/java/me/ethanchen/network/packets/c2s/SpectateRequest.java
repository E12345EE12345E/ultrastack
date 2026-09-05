package me.ethanchen.network.packets.c2s;

import me.ethanchen.network.packets.NetworkPacket;

/** Ask the room to resend a full gamestate snapshot so this client can enter spectate. */
public class SpectateRequest extends NetworkPacket {
    // empty marker packet
}
