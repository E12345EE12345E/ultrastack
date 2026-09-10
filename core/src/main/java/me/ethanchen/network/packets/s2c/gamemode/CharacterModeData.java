package me.ethanchen.network.packets.s2c.gamemode;

/** Per-slot character meter state, sent inside {@code LightGameStateBroadcast} for CHARACTER_ modes. */
public class CharacterModeData {
    public int[] characterIds;
    public float[] meterFill;
    public float[] meterMax;
    /** Per-board fall-speed factors from The Noob's ability ({@code 0} = frozen, {@code 1} = normal). */
    public float[] boardGravitySpeedFactor;
}
