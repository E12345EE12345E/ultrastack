package me.ethanchen.network.packets.s2c;

import me.ethanchen.network.packets.NetworkPacket;

/** Cosmetic cue that a seated player successfully activated their character ability. */
public class AbilityActivateBroadcast extends NetworkPacket {
    /** Player index who activated their ability. */
    public byte playerId;
}
