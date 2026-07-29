package me.ethanchen.network.packets.c2s;

import me.ethanchen.network.packets.NetworkPacket;

/** Client reports how many local players it wants seated in the current room. */
public class LocalPlayerCountRequest extends NetworkPacket {
    public byte count;
}
