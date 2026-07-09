package me.ethanchen.game.board;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import me.ethanchen.network.dto.NetQueue;

/**
 * Characterization tests locking in {@link PieceQueue}'s 7-bag determinism, which the
 * client/server rely on staying in sync via {@code seed} + {@code alreadyGeneratedNumber}.
 */
class PieceQueueTest {

    @Test
    void sameSeedProducesIdenticalSequence() {
        PieceQueue q1 = new PieceQueue(42, PieceQueue.BagTypes.BAG_7);
        PieceQueue q2 = new PieceQueue(42, PieceQueue.BagTypes.BAG_7);

        byte[] seq1 = takeN(q1, 21);
        byte[] seq2 = takeN(q2, 21);

        assertArrayEquals(seq1, seq2);
    }

    @Test
    void eachBagIsAPermutationOfAllSevenPieces() {
        PieceQueue q = new PieceQueue(7, PieceQueue.BagTypes.BAG_7);
        Set<Byte> expected = new HashSet<>();
        for (byte b : PieceQueue.BagTypes.BAG_7.get()) expected.add(b);

        for (int bag = 0; bag < 5; bag++) {
            Set<Byte> actual = new HashSet<>();
            for (int i = 0; i < 7; i++) actual.add(q.takeNext());
            assertEquals(expected, actual, "bag " + bag + " should contain each piece exactly once");
        }
    }

    @Test
    void netQueueRoundTripResumesAtSamePoint() {
        PieceQueue original = new PieceQueue(123, PieceQueue.BagTypes.BAG_7);
        // Consume across a bag boundary so alreadyGeneratedNumber > 0 and the partial
        // bag remainder is non-trivial.
        for (int i = 0; i < 10; i++) original.takeNext();

        NetQueue net = original.convertToNetQueue();
        PieceQueue resumed = PieceQueue.createFromNetQueue(net);

        byte[] fromOriginal = takeN(original, 10);
        byte[] fromResumed = takeN(resumed, 10);
        assertArrayEquals(fromOriginal, fromResumed);
    }

    private static byte[] takeN(PieceQueue q, int n) {
        byte[] result = new byte[n];
        for (int i = 0; i < n; i++) result[i] = q.takeNext();
        return result;
    }
}
