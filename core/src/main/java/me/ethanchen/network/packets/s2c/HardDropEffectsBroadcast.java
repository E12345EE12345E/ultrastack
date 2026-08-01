package me.ethanchen.network.packets.s2c;

import me.ethanchen.network.dto.HardDropEffect;
import me.ethanchen.network.packets.NetworkPacket;

/**
 * Batched broadcast of all piece placements ("hard drop" effects) that occurred since the last
 * network update tick. Replaces the old separate {@code PlacementSoundBroadcast} and the
 * {@code TYPE_HARD_DROP} {@link ParticleSpawner} with a single packet.
 *
 * <p><strong>Purely cosmetic:</strong> sent unreliably over UDP alongside
 * {@link LightGameStateBroadcast}. Entries may be dropped in transit and must never drive game
 * logic or authoritative client state — only sound, particle, and visual effects (e.g. the
 * player ripple-circle "poof").
 */
public class HardDropEffectsBroadcast extends NetworkPacket {
    public HardDropEffect[] effects;
}
