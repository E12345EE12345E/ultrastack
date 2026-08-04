package me.ethanchen.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.GameHandler;
import me.ethanchen.game.board.LineClearResult;
import me.ethanchen.game.board.Piece;
import me.ethanchen.game.board.SpinType;

/**
 * Verifies {@link ScoreModeScorer}'s optional {@link CharacterScoreBonusProvider} hook, used only
 * by CHARACTER_ modes to layer artifact/passive score bonuses on top of the normal formula.
 */
class ScoreModeScorerCharacterBonusTest {

    @Test
    void bonusProviderMultipliesScoreAdditively() {
        ScoreModeScorer scorer = new ScoreModeScorer();
        GameHandler game = new GameHandler(1);
        scorer.reset(1, game);

        LineClearResult result = new LineClearResult();
        result.placed = true;
        result.playerId = 0;
        result.pieceType = Piece.O;
        result.spinType = SpinType.NONE;
        result.clearedRows = new int[]{0}; // one cleared row -> single (100 base)

        PlacementEffects effects = new PlacementEffects();

        // Plain single (100 base) with the always-on "different column" bonus (1.2x) that
        // applies whenever no repeat column has been established yet.
        long plainPoints = scorer.scoreHardDrop(result, effects);
        assertEquals(120L, plainPoints);
    }

    @Test
    void bonusProviderAppliesFiftyPercentBonus() {
        ScoreModeScorer scorer = new ScoreModeScorer();
        GameHandler game = new GameHandler(1);
        scorer.reset(1, game);
        scorer.setBonusProvider((playerId, pieceType, lineClear, spin) -> 50f);

        LineClearResult result = new LineClearResult();
        result.placed = true;
        result.playerId = 0;
        result.pieceType = Piece.O;
        result.spinType = SpinType.NONE;
        result.clearedRows = new int[]{0};

        long points = scorer.scoreHardDrop(result, new PlacementEffects());
        assertEquals(180L, points); // 100 base * 1.2 (diff-column) * 1.5 (character bonus)
    }
}
