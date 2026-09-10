package me.ethanchen.network.packets.s2c;

import me.ethanchen.game.progression.Artifact;
import me.ethanchen.network.packets.NetworkPacket;

/** Complete result of a Card Dealer request. */
public class DealerResultBroadcast extends NetworkPacket {
    public boolean success;
    public String reason;
    public byte[] rarities;
    public Artifact[] artifacts;
    public long tokensSpent;
}
