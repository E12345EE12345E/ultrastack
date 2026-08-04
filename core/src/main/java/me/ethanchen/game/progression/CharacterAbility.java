package me.ethanchen.game.progression;

/** Identifies which server-side active-ability behavior a character uses (implementation.md, Part 4). */
public enum CharacterAbility {
    /** Swaps the current piece with an I3 or L3, 50% chance of each. */
    RANDOM_I3_OR_L3,
    /** Replaces the current piece with an I. */
    FORCE_I
}
