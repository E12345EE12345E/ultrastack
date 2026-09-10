package me.ethanchen.game.progression;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Rolls complete, server-authoritative Card Dealer results. */
public final class GachaRoll {
    public static final class Card {
        public final byte rarity;
        public final Artifact artifact;

        public Card(byte rarity, Artifact artifact) {
            this.rarity = rarity;
            this.artifact = artifact;
        }
    }

    private GachaRoll() {}

    public static List<Card> deal(int count, Random rng) {
        if (count != 1 && count != 10) {
            throw new IllegalArgumentException("dealer count must be 1 or 10");
        }
        if (rng == null) throw new IllegalArgumentException("rng is required");

        List<Card> cards = new ArrayList<>(count);
        boolean hasEpicOrBetter = false;
        for (int i = 0; i < count; i++) {
            Card card = rollCard(GachaTables.rollRarity(rng), rng);
            cards.add(card);
            hasEpicOrBetter |= card.rarity >= GachaTables.EPIC;
        }

        if (count == 10 && !hasEpicOrBetter) {
            int guaranteedIndex = rng.nextInt(cards.size());
            cards.set(guaranteedIndex, rollCard(GachaTables.EPIC, rng));
        }
        return cards;
    }

    private static Card rollCard(byte rarity, Random rng) {
        GachaTables.Outcome outcome = GachaTables.rollOutcome(rarity, rng);
        Artifact artifact = ArtifactRoller.roll(
                outcome.pieceType, outcome.level, outcome.baseQuality, rng);
        return new Card(rarity, artifact);
    }
}
