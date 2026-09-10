package me.ethanchen.headless;

import me.ethanchen.game.progression.BestGameRecord;
import me.ethanchen.game.progression.PlayerProfile;

/**
 * Wrapper stored (as JSON) in {@code accounts.extra_json}. Wrapping the profile rather than
 * writing it directly leaves room for unrelated forward-compatible fields later without another
 * schema change.
 */
public class AccountExtra {
    public PlayerProfile profile;
    public BestGameRecord bestScore;
    public BestGameRecord bestPuzzle;
    public BestGameRecord bestCharacterScore;
    /**
     * True after the account has received its one-time starting token grant. Missing/false in
     * legacy JSON causes the grant to be applied on the next successful login.
     */
    public boolean receivedInitialTokenBonus;
    /**
     * True after a one-shot {@code game_results} backfill (or for accounts created after bests
     * existed). Missing/false on legacy extra_json means the next profile view should migrate.
     */
    public boolean bestsMigrated;

    public AccountExtra() {} // required for libGDX Json deserialization

    public BestGameRecord recordFor(String gamemode) {
        if ("MULTIPLAYER_SCORE".equals(gamemode)) return bestScore;
        if ("MULTIPLAYER_PUZZLE".equals(gamemode)) return bestPuzzle;
        if ("CHARACTER_SCORE".equals(gamemode)) return bestCharacterScore;
        return null;
    }

    public void setRecord(BestGameRecord record) {
        if (record == null || record.gamemode == null) return;
        switch (record.gamemode) {
            case "MULTIPLAYER_SCORE":
                bestScore = record;
                break;
            case "MULTIPLAYER_PUZZLE":
                bestPuzzle = record;
                break;
            case "CHARACTER_SCORE":
                bestCharacterScore = record;
                break;
            default:
                break;
        }
    }
}
