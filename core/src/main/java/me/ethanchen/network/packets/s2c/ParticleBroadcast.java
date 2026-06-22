package me.ethanchen.network.packets.s2c;

import me.ethanchen.network.packets.NetworkPacket;

public class ParticleBroadcast extends NetworkPacket {
    /** Individual particle events. Kept for future use cases that need per-particle control. */
    public NetParticle[] particles;

    /** Compact spawner objects for bulk effects (hard-drop flash, line-clear tile-break). */
    public ParticleSpawner[] spawners;
}
