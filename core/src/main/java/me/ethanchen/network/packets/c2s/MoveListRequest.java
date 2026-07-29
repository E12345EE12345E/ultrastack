package me.ethanchen.network.packets.c2s;

import me.ethanchen.network.packets.NetworkPacket;

public class MoveListRequest extends NetworkPacket {
    public int[] ids;    // ascending move ids
    public byte[] types; // MoveType ordinals, parallel to ids
    /** Which of the sender's local players these moves belong to (0 = main). */
    public byte localIndex;
}
