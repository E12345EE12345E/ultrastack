package me.ethanchen.game.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class GachaRollTest {
    @Test
    void tenDealAlwaysContainsEpicOrBetter() {
        for (int seed = 0; seed < 500; seed++) {
            List<GachaRoll.Card> cards = GachaRoll.deal(10, new Random(seed));
            assertEquals(10, cards.size());
            assertTrue(cards.stream().anyMatch(card -> card.rarity >= GachaTables.EPIC));
        }
    }

    @Test
    void everyConfiguredOutcomeIsValid() {
        for (byte rarity = GachaTables.RARE; rarity <= GachaTables.LEGENDARY; rarity++) {
            for (GachaTables.Outcome outcome : GachaTables.tableFor(rarity)) {
                assertTrue(outcome.weight > 0);
                assertTrue(Artifact.isTetrominoType(outcome.pieceType));
                assertTrue(outcome.level > 0);
                assertTrue(outcome.baseQuality >= 0f && outcome.baseQuality <= 100f);
            }
        }
    }

    @Test
    void rejectsUnsupportedDealCounts() {
        assertThrows(IllegalArgumentException.class, () -> GachaRoll.deal(0, new Random(0)));
        assertThrows(IllegalArgumentException.class, () -> GachaRoll.deal(11, new Random(0)));
    }
}
