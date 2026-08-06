package me.ethanchen.game.progression;

/** Identifies which server-side active-ability behavior a character uses (implementation.md, Part 4). */
public enum CharacterAbility {
    /**
     * Fills empty cells in the skyline band (between the shortest and tallest column peaks)
     * with connection-state-0 garbage, skipping cells occupied by active player pieces.
     */
    FILL_SKYLINE_GAPS,
    /** Replaces the current piece with an I. */
    FORCE_I,
    /**
     * Disables gravity for all players for 10s, then linearly ramps fall speed back over 5s.
     * While gravity is disabled, all players' passive meter fill is multiplied by
     * {@code 1 + activationCount}. Additional activations during the disable window stack only
     * the meter multiplier; activations during the ramp window restart the 10s disable.
     */
    DISABLE_AND_RAMP_GRAVITY
}
