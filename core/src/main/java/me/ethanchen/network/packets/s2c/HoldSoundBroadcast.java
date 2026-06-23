package me.ethanchen.network.packets.s2c;

import me.ethanchen.network.packets.NetworkPacket;

public class HoldSoundBroadcast extends NetworkPacket {
    /** Player index who performed the hold. */
    public byte playerId;
}
