package me.ethanchen.network.packets.s2c;

import me.ethanchen.game.progression.Artifact;
import me.ethanchen.network.packets.NetworkPacket;

/** Reply to a {@code FusionRequest}. On success, {@link #result} is the newly created artifact. */
public class FusionResultBroadcast extends NetworkPacket {
    public boolean success;
    public String reason;
    public Artifact result;
}
