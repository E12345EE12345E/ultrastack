package me.ethanchen.game.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class PieceQueuePeekTest {

    @Test
    void peekDoesNotConsume() {
        PieceQueue q = new PieceQueue(12345, PieceQueue.BagTypes.BAG_7);
        byte first = q.peek(0);
        byte[] many = q.peekMany(3);
        assertEquals(first, many[0]);
        assertNotEquals(0, first);
        assertEquals(first, q.takeNext());
        assertEquals(many[1], q.peek(0));
        assertEquals(many[1], q.takeNext());
        assertEquals(many[2], q.peek(0));
    }
}
