package me.ethanchen.network.packets.c2s;

import me.ethanchen.network.packets.NetworkPacket;

/** Requests fusing exactly 5 owned, same-level, non-equipped artifacts into a new one. */
public class FusionRequest extends NetworkPacket {
    public String[] artifactIds;
}
