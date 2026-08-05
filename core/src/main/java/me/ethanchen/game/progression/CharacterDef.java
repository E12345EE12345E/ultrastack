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

    public CharacterDef(int id, String name, float scoreMeterMultiplier, float perSecondMeterFill,
                         float meterRequired, CharacterAbility ability, PieceQueue.BagTypes bagOverride,
                         byte[] passiveBonusPieceTypes, float passiveLineClearScoreBonusPercent,
                         float passiveMeterOtherMultiplier, float passiveMeterSelfMultiplier) {
        this.id = id;
        this.name = name;
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
    }

    public boolean hasPassiveBonusFor(byte pieceType) {
        if (passiveBonusPieceTypes == null) return false;
        for (byte t : passiveBonusPieceTypes) if (t == pieceType) return true;
        return false;
    }

    /** id 0: (Placeholder Name) 3-Mino. */
    public static final CharacterDef THREE_MINO = new CharacterDef(
            0, "3-Mino",
            0.25f, 100f, 6000f,
            CharacterAbility.FILL_SKYLINE_GAPS, PieceQueue.BagTypes.BAG_3MINO,
            new byte[]{Piece.L3}, 100f,
            4.0f, 2.0f);

    /** id 1: (Placeholder Name) Wizard. */
    public static final CharacterDef WIZARD = new CharacterDef(
            1, "Wizard",
            2.0f, 20f, 2000f,
            CharacterAbility.FORCE_I, PieceQueue.BagTypes.BAG_WIZARD,
            new byte[]{Piece.I}, 50f,
            4.0f, 0.0f);
}
