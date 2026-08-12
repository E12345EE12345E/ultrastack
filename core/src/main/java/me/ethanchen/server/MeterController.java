package me.ethanchen.server;

import java.util.Arrays;

import me.ethanchen.game.GameHandler;
import me.ethanchen.game.progression.CharacterDef;
import me.ethanchen.network.packets.s2c.gamemode.CharacterModeData;

/**
 * Owns each seated player's active-ability meter for CHARACTER_ modes (implementation.md, Part 1).
 * Score gains from a placement fill every player's meter <em>on that placer's board only</em>,
 * each scaled by the receiving player's own character rate and equipped artifacts; meters also
 * fill slowly over time independent of scoring. Meters are indexed by global session slot (one
 * controller instance for the whole session), but {@link #onScoreEvent} and the passive-fill
 * multiplier are scoped per board via {@code game}'s slot/board mapping so an ability or artifact
 * on one board can never fill another board's meters.
 */
class MeterController {
    private int players;
    private ActiveLoadout[] loadouts;
    private float[] meter;
    private GameHandler game;
    /** Extra multiplier on passive (time-based) fill, indexed by board, e.g. from The Noob's ability. */
    private float[] externalPassiveFillMultiplier = new float[0];

    void reset(int players, ActiveLoadout[] loadouts, GameHandler game, int numBoards) {
        this.players = players;
        this.loadouts = loadouts;
        this.meter = new float[players];
        this.game = game;
        this.externalPassiveFillMultiplier = new float[numBoards];
        Arrays.fill(this.externalPassiveFillMultiplier, 1f);
    }

    /** Clears all state, e.g. when the game stops. */
    void clear() {
        players = 0;
        loadouts = null;
        meter = null;
        game = null;
        externalPassiveFillMultiplier = new float[0];
    }

    void setExternalPassiveFillMultiplier(int boardIndex, float multiplier) {
        if (boardIndex < 0 || boardIndex >= externalPassiveFillMultiplier.length) return;
        externalPassiveFillMultiplier[boardIndex] = multiplier > 0f ? multiplier : 1f;
    }

    private ActiveLoadout loadoutFor(int playerId) {
        if (loadouts == null || playerId < 0 || playerId >= loadouts.length) return null;
        return loadouts[playerId];
    }

    private float externalMultiplierFor(int playerId) {
        if (game == null || externalPassiveFillMultiplier.length == 0) return 1f;
        int boardIndex = game.boardIndexOf(playerId);
        return (boardIndex >= 0 && boardIndex < externalPassiveFillMultiplier.length)
                ? externalPassiveFillMultiplier[boardIndex] : 1f;
    }

    /** Passive per-second meter fill, independent of scoring. */
    void tickPassive(float deltaSeconds) {
        if (meter == null) return;
        for (int i = 0; i < players; i++) {
            ActiveLoadout loadout = loadoutFor(i);
            if (loadout == null || loadout.character == null) continue;
            float fill = loadout.character.perSecondMeterFill;
            fill *= (1f + loadout.equippedPassiveFillBonusPercent() / 100f);
            fill *= externalMultiplierFor(i);
            addToMeter(i, fill * deltaSeconds);
        }
    }

    /**
     * Called once per scored placement (lines > 0). Fills the meter of every player seated on
     * the placer's own board (never a different board) from the placer's score gain, applying:
     * the receiver's own character rate, the placer's piece-specific artifact meter bonus
     * (effects c/d, applies to everyone on that board), the receiver's own "while equipped"
     * artifact bonus (effects e/f, applies only to their own meter), and the placer's character
     * passive asymmetric multiplier (e.g. 3-Mino's 4x-others/1x-self on I3/L3).
     */
    void onScoreEvent(int placerId, long scoreGain, byte pieceType, boolean lineClear, boolean spin) {
        if (meter == null || scoreGain <= 0 || game == null) return;
        ActiveLoadout placerLoadout = loadoutFor(placerId);
        float piecewideBonusPercent = placerLoadout != null
                ? placerLoadout.pieceMeterBonusPercent(pieceType, lineClear, spin) : 0f;
        CharacterDef placerChar = placerLoadout != null ? placerLoadout.character : null;
        boolean passiveApplies = lineClear && placerChar != null && placerChar.hasPassiveBonusFor(pieceType);

        int boardIndex = game.boardIndexOf(placerId);
        for (int i : game.slotsOnBoard(boardIndex)) {
            ActiveLoadout recvLoadout = loadoutFor(i);
            if (recvLoadout == null || recvLoadout.character == null) continue;
            float charMultiplier = recvLoadout.character.scoreMeterMultiplier;
            float passiveMultiplier = 1f;
            if (passiveApplies) {
                passiveMultiplier = (i == placerId)
                        ? placerChar.passiveMeterSelfMultiplier
                        : placerChar.passiveMeterOtherMultiplier;
            }
            float selfBonusPercent = recvLoadout.equippedMeterBonusPercent(lineClear, spin);
            float delta = scoreGain * charMultiplier * passiveMultiplier
                    * (1f + piecewideBonusPercent / 100f)
                    * (1f + selfBonusPercent / 100f);
            addToMeter(i, delta);
        }
    }

    /** Consumes {@code playerId}'s full meter if it's ready; returns false (no-op) otherwise. */
    boolean tryConsume(int playerId) {
        ActiveLoadout loadout = loadoutFor(playerId);
        if (loadout == null || loadout.character == null || meter == null) return false;
        if (playerId < 0 || playerId >= meter.length) return false;
        if (meter[playerId] < loadout.character.meterRequired) return false;
        meter[playerId] = 0f;
        return true;
    }

    private void addToMeter(int playerId, float amount) {
        if (playerId < 0 || playerId >= meter.length) return;
        ActiveLoadout loadout = loadoutFor(playerId);
        float max = (loadout != null && loadout.character != null) ? loadout.character.meterRequired : Float.MAX_VALUE;
        meter[playerId] = Math.min(max, meter[playerId] + amount);
    }

    CharacterModeData getCharacterModeData() {
        CharacterModeData d = new CharacterModeData();
        if (meter == null) {
            d.meterFill = new float[0];
            d.meterMax = new float[0];
            d.characterIds = new int[0];
            return d;
        }
        d.meterFill = java.util.Arrays.copyOf(meter, meter.length);
        d.meterMax = new float[players];
        d.characterIds = new int[players];
        for (int i = 0; i < players; i++) {
            ActiveLoadout loadout = loadoutFor(i);
            if (loadout != null && loadout.character != null) {
                d.meterMax[i] = loadout.character.meterRequired;
                d.characterIds[i] = loadout.character.id;
            } else {
                d.meterMax[i] = 0f;
                d.characterIds[i] = -1;
            }
        }
        return d;
    }
}
