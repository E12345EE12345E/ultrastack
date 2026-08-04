package me.ethanchen.server;

import me.ethanchen.game.progression.CharacterDef;
import me.ethanchen.network.packets.s2c.gamemode.CharacterModeData;

/**
 * Owns each seated player's active-ability meter for CHARACTER_ modes (implementation.md, Part 1).
 * Score gains from any player fill every player's meter simultaneously, each scaled by the
 * receiving player's own character rate and equipped artifacts; meters also fill slowly over
 * time independent of scoring.
 */
class MeterController {
    private int players;
    private ActiveLoadout[] loadouts;
    private float[] meter;

    void reset(int players, ActiveLoadout[] loadouts) {
        this.players = players;
        this.loadouts = loadouts;
        this.meter = new float[players];
    }

    private ActiveLoadout loadoutFor(int playerId) {
        if (loadouts == null || playerId < 0 || playerId >= loadouts.length) return null;
        return loadouts[playerId];
    }

    /** Passive per-second meter fill, independent of scoring. */
    void tickPassive(float deltaSeconds) {
        if (meter == null) return;
        for (int i = 0; i < players; i++) {
            ActiveLoadout loadout = loadoutFor(i);
            if (loadout == null || loadout.character == null) continue;
            float fill = loadout.character.perSecondMeterFill;
            fill *= (1f + loadout.equippedPassiveFillBonusPercent() / 100f);
            addToMeter(i, fill * deltaSeconds);
        }
    }

    /**
     * Called once per scored placement (lines > 0). Fills every player's meter from the
     * placer's score gain, applying: the receiver's own character rate, the placer's
     * piece-specific artifact meter bonus (effects c/d, applies to everyone), the receiver's own
     * "while equipped" artifact bonus (effects e/f, applies only to their own meter), and the
     * placer's character passive asymmetric multiplier (e.g. 3-Mino's 4x-others/1x-self on I3/L3).
     */
    void onScoreEvent(int placerId, long scoreGain, byte pieceType, boolean lineClear, boolean spin) {
        if (meter == null || scoreGain <= 0) return;
        ActiveLoadout placerLoadout = loadoutFor(placerId);
        float piecewideBonusPercent = placerLoadout != null
                ? placerLoadout.pieceMeterBonusPercent(pieceType, lineClear, spin) : 0f;
        CharacterDef placerChar = placerLoadout != null ? placerLoadout.character : null;
        boolean passiveApplies = lineClear && placerChar != null && placerChar.hasPassiveBonusFor(pieceType);

        for (int i = 0; i < players; i++) {
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
