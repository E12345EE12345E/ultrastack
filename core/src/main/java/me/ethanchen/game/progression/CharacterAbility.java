package me.ethanchen.game.progression;

/** Identifies which server-side active-ability behavior a character uses (implementation.md, Part 4). */
public enum CharacterAbility {
    /**
     * Fills empty cells in the skyline band (between the shortest and tallest column peaks)
     * with connection-state-0 garbage, skipping cells occupied by active player pieces.
     */
    FILL_SKYLINE_GAPS,
    /** Replaces the current piece with an I. */
    FORCE_I
}
