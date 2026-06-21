package me.ethanchen.network.packets.s2c.gamemode;

/** Sent as part of {@link me.ethanchen.network.packets.s2c.EndGameBroadcast} for MULTIPLAYER_SCORE mode. */
public class ScoreModeEndData {
    /** The final cooperative score achieved by all players. */
    public long finalScore;
}
