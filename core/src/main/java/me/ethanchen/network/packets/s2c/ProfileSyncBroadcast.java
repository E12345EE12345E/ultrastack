package me.ethanchen.network.packets.s2c;

import me.ethanchen.game.progression.PlayerProfile;
import me.ethanchen.network.packets.NetworkPacket;

/**
 * Sent after successful login/register (account mode) or after {@code JoinResponse} (LAN mode),
 * and again any time the profile changes (loadout change, artifact grant, fusion result).
 */
public class ProfileSyncBroadcast extends NetworkPacket {
    public PlayerProfile profile;
    /** True in LAN mode: profile is session-only, acquisition/fusion are disabled (implementation.md, Part 5). */
    public boolean readOnly;
}
