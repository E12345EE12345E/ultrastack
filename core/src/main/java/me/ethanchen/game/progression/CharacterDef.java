package me.ethanchen.game.progression;

import me.ethanchen.game.board.Piece;
import me.ethanchen.game.board.PieceQueue;

/**
 * Static definition of a playable character: meter fill rates, active ability, and any
 * always-on passive behavior (implementation.md, Part 4). Unlocked characters have identical
 * abilities for all players -- there is no per-player leveling of characters themselves.
 */
public final class CharacterDef {
    public final int id;
    public final String name;
    /** Short paragraph describing the active ability (shown on the character screen). */
    public final String abilityDescription;
    /** Short paragraph describing the character / passives (shown on the character screen). */
    public final String description;
    /** Classpath-relative (assets/) path to this character's portrait image. */
    public final String portraitFile;

    /** Multiplier applied to a player's own score gain before it contributes to meter fill. */
    public final float scoreMeterMultiplier;
    /** Passive meter fill per second, independent of score. */
    public final float perSecondMeterFill;
    /** Meter amount required to activate the ability. */
    public final float meterRequired;
    public final CharacterAbility ability;

    /** Non-null if this character forces a specific piece queue bag (e.g. 3-Mino, Wizard). */
    public final PieceQueue.BagTypes bagOverride;

    /** Piece types (if any) that receive a flat passive score bonus on line clears, e.g. 3-Mino's I3/L3. */
    public final byte[] passiveBonusPieceTypes;
    /** Percent bonus applied to line-clear score for {@link #passiveBonusPieceTypes}. */
    public final float passiveLineClearScoreBonusPercent;
    /** Meter-fill multiplier applied to other players when this player clears with {@link #passiveBonusPieceTypes}. */
    public final float passiveMeterOtherMultiplier;
    /** Meter-fill multiplier applied to this player's own meter for the same clears. */
    public final float passiveMeterSelfMultiplier;
    /**
     * Always-on multiplier on this player's own fall speed (1 = normal, 0.5 = half gravity).
     * Does not affect other players.
     */
    public final float passiveGravitySpeedMultiplier;

    public CharacterDef(int id, String name, String abilityDescription, String description,
                         float scoreMeterMultiplier, float perSecondMeterFill,
                         float meterRequired, CharacterAbility ability, PieceQueue.BagTypes bagOverride,
                         byte[] passiveBonusPieceTypes, float passiveLineClearScoreBonusPercent,
                         float passiveMeterOtherMultiplier, float passiveMeterSelfMultiplier,
                         float passiveGravitySpeedMultiplier) {
        this.id = id;
        this.name = name;
        this.abilityDescription = abilityDescription;
        this.description = description;
        // No distinct per-character art yet; every character shares the same placeholder portrait.
        this.portraitFile = "char_img/placeholder_character.png";
        this.scoreMeterMultiplier = scoreMeterMultiplier;
        this.perSecondMeterFill = perSecondMeterFill;
        this.meterRequired = meterRequired;
        this.ability = ability;
        this.bagOverride = bagOverride;
        this.passiveBonusPieceTypes = passiveBonusPieceTypes;
        this.passiveLineClearScoreBonusPercent = passiveLineClearScoreBonusPercent;
        this.passiveMeterOtherMultiplier = passiveMeterOtherMultiplier;
        this.passiveMeterSelfMultiplier = passiveMeterSelfMultiplier;
        this.passiveGravitySpeedMultiplier = passiveGravitySpeedMultiplier;
    }

    public boolean hasPassiveBonusFor(byte pieceType) {
        if (passiveBonusPieceTypes == null) return false;
        for (byte t : passiveBonusPieceTypes) if (t == pieceType) return true;
        return false;
    }

    /** id 0: (Placeholder Name) 3-Mino. */
    public static final CharacterDef THREE_MINO = new CharacterDef(
            0, "3-Mino",
            "Fills overhangs.",
            "Queue {L3, L3, I3, I3} plus a random J/L/S/Z/O. 3mino clears fill other meters 2x.",
            0.25f, 100f, 6000f,
            CharacterAbility.FILL_SKYLINE_GAPS, PieceQueue.BagTypes.BAG_3MINO,
            new byte[]{Piece.L3, Piece.I3}, 0f,
            2.0f, 1.0f, 1.0f);

    /** id 1: (Placeholder Name) Wizard. */
    public static final CharacterDef WIZARD = new CharacterDef(
            1, "Wizard",
            "Replaces current piece with an avalanche I.",
            "Queue {J, L, S, T, Z}. I clears score 50% more and fills other meters 4x.",
            2.0f, 20f, 2000f,
            CharacterAbility.FORCE_I, PieceQueue.BagTypes.BAG_WIZARD,
            new byte[]{Piece.I}, 50f,
            4.0f, 0.0f, 1.0f);

    /** id 2: (Placeholder Name) The Noob. Default selected character for new accounts. */
    public static final CharacterDef NOOB = new CharacterDef(
            2, "The Noob",
            "Disables gravity and increases passive meter fill for everyone for 10s. Causes an avalanche.",
            "Always has half gravity for self.",
            1.0f, 200f, 10000f,
            CharacterAbility.DISABLE_AND_RAMP_GRAVITY, null,
            null, 0f,
            1.0f, 1.0f, 0.5f);
}
