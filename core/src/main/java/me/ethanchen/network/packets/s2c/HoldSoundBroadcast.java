package me.ethanchen.network.packets.s2c;

import me.ethanchen.network.packets.NetworkPacket;

public class HoldSoundBroadcast extends NetworkPacket {
    /** Player index who attempted the hold. */
    public byte playerId;
    /** Index of the board this hold occurred on. */
    public byte boardIndex;
    /** True if the hold succeeded; false if it was blocked by that board's hold cooldown. */
    public boolean success;
}
