package me.ethanchen.network.packets.c2s;

import me.ethanchen.network.packets.NetworkPacket;

public class MoveListRequest extends NetworkPacket {
    public int[] ids;    // ascending move ids
    public byte[] types; // MoveType ordinals, parallel to ids
}
