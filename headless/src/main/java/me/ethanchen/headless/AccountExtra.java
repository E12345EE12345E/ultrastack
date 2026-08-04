package me.ethanchen.headless;

import me.ethanchen.game.progression.PlayerProfile;

/**
 * Wrapper stored (as JSON) in {@code accounts.extra_json}. Wrapping the profile rather than
 * writing it directly leaves room for unrelated forward-compatible fields later without another
 * schema change.
 */
public class AccountExtra {
    public PlayerProfile profile;

    public AccountExtra() {} // required for libGDX Json deserialization
}
