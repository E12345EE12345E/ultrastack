package me.ethanchen.game.board;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import me.ethanchen.network.dto.NetQueue;

class PieceQueueDeterminismTest {

    @Test
    void sameSeedProducesIdenticalSequence() {
        byte[] a = take(new PieceQueue(42, PieceQueue.BagTypes.BAG_7), 200);
        byte[] b = take(new PieceQueue(42, PieceQueue.BagTypes.BAG_7), 200);
        assertArrayEquals(a, b);
    }

    @Test
    void netQueueRoundTripPreservesUpcomingAndSubsequent() {
        PieceQueue src = new PieceQueue(99, PieceQueue.BagTypes.BAG_7);
        src.takeNext();
        src.takeNext();
        byte[] upcoming = src.peekMany(14);
        NetQueue net = src.convertToNetQueue();
        PieceQueue restored = PieceQueue.createFromNetQueue(net);
        assertArrayEquals(upcoming, restored.peekMany(14));
        assertEquals(src.takeNext(), restored.takeNext());
        assertEquals(src.takeNext(), restored.takeNext());
    }

    @Test
    void threeMinoBagStaysDeterministic() {
        byte[] a = take(new PieceQueue(7, PieceQueue.BagTypes.BAG_3MINO), 80);
        byte[] b = take(new PieceQueue(7, PieceQueue.BagTypes.BAG_3MINO), 80);
        assertArrayEquals(a, b);
    }

    private static byte[] take(PieceQueue q, int n) {
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) out[i] = q.takeNext();
        return out;
    }
}
