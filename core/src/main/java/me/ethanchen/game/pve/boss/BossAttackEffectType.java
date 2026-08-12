package me.ethanchen.game.pve.boss;

/** Instant (or near-instant) effect applied when a {@link BossAttack} enters its ATTACK phase. */
public enum BossAttackEffectType {
    /** Push {@code amount} garbage rows onto every still-running board, using {@code style}. */
    ADD_GARBAGE
}
