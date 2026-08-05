package me.ethanchen.network.packets.c2s;

import me.ethanchen.network.packets.NetworkPacket;

/** Requests activation of the sender's character active ability (implementation.md, Part 1/4). */
public class AbilityRequest extends NetworkPacket {
    public byte localIndex;
}
