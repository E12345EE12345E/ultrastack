package me.ethanchen.network.packets.s2c;

import me.ethanchen.game.progression.Artifact;
import me.ethanchen.network.packets.NetworkPacket;

/** Sent alongside {@link EndGameBroadcast} when a victory grants a new artifact (implementation.md, Part 2). */
public class ArtifactGrantBroadcast extends NetworkPacket {
    public Artifact artifact;
}
