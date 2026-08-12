package me.ethanchen.server;

/**
 * Computes the additional percentage score bonus (artifact effects a/b plus any character
 * passive) for a placement, injected into score-mode scorers only for CHARACTER_ modes.
 */
interface CharacterScoreBonusProvider {
    float scoreBonusPercent(int playerId, byte pieceType, boolean lineClear, boolean spin);
}
