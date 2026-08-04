package me.ethanchen.network.packets.c2s;

import me.ethanchen.network.packets.NetworkPacket;

/** Requests a change to the sender's selected character and/or equipped artifacts. */
public class LoadoutRequest extends NetworkPacket {
    public int characterId;
    /** Equipped artifact ids; either may be null/empty for an empty slot. */
    public String artifactIdA;
    public String artifactIdB;
}
