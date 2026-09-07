package me.ethanchen.game.board;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

import me.ethanchen.network.dto.NetQueue;

public class PieceQueue {
    protected final int seed;
    private int generationNumber;
    private final Random random;
    protected final BagTypes bag;
    private byte[] buf;
    private int head;
    private int size;

    public PieceQueue(int seed, BagTypes bag) {
        this.seed = seed;
        this.bag = bag;
        this.generationNumber = 0;
        this.random = new Random(seed);
        this.buf = new byte[16];
    }

    public PieceQueue(int seed, BagTypes bag, ArrayList<Integer> piecesAlreadyInBag, int alreadyGeneratedNumber) {
        this(seed, bag);
        if (piecesAlreadyInBag != null) {
            for (Integer id : piecesAlreadyInBag) {
                enqueue((byte) (int) id);
            }
        }
        while (this.generationNumber < alreadyGeneratedNumber) {
            this.random.nextInt();
            this.generationNumber++;
        }
    }

    public void refill() {
        while (this.size < bag.get().length) {
            addGeneratedBag();
        }
    }

    public byte takeNext() {
        refill();
        byte v = buf[head];
        head = (head + 1) % buf.length;
        size--;
        return v;
    }

    /** Returns the upcoming piece at {@code index} (0 = next to spawn) without consuming it. */
    public byte peek(int index) {
        if (index < 0) return 0;
        ensureAvailable(index + 1);
        return buf[(head + index) % buf.length];
    }

    /** Returns the next {@code count} upcoming piece types without consuming them. */
    public byte[] peekMany(int count) {
        int n = Math.max(0, count);
        byte[] out = new byte[n];
        if (n == 0) return out;
        ensureAvailable(n);
        for (int i = 0; i < n; i++) {
            out[i] = buf[(head + i) % buf.length];
        }
        return out;
    }

    private void ensureAvailable(int count) {
        refill();
        while (size < count) {
            addGeneratedBag();
        }
    }

    private static final byte[] BAG_3MINO_EXTRAS = {
        Piece.J, Piece.L, Piece.S, Piece.Z, Piece.O
    };

    /**
     * Same shuffle as the previous ArrayList implementation: {@code new Random(this.random.nextInt())}
     * plus {@link Collections#shuffle} on a temporary list, then copied into the ring buffer.
     */
    private void addGeneratedBag() {
        ArrayList<Integer> shuffleBag = new ArrayList<Integer>();
        for (byte b : bag.get()) shuffleBag.add((int) b);

        // One nextInt() per bag keeps NetQueue reconstruction in sync (see ctor that burns RNG).
        Random bagRng = new Random(this.random.nextInt());
        if (bag == BagTypes.BAG_3MINO) {
            shuffleBag.add((int) BAG_3MINO_EXTRAS[bagRng.nextInt(BAG_3MINO_EXTRAS.length)]);
        }
        Collections.shuffle(shuffleBag, bagRng);

        this.generationNumber++;
        for (Integer id : shuffleBag) {
            enqueue((byte) (int) id);
        }
    }

    private void enqueue(byte value) {
        ensureCap(size + 1);
        buf[(head + size) % buf.length] = value;
        size++;
    }

    private void ensureCap(int min) {
        if (buf.length >= min) return;
        int cap = buf.length;
        while (cap < min) cap *= 2;
        byte[] next = new byte[cap];
        for (int i = 0; i < size; i++) {
            next[i] = buf[(head + i) % buf.length];
        }
        buf = next;
        head = 0;
    }

    public NetQueue convertToNetQueue() {
        NetQueue nq = new NetQueue();
        nq.seed = seed;
        nq.bag = bag;
        nq.piecesAlreadyInBag = new byte[size];
        for (int i = 0; i < size; i++) {
            nq.piecesAlreadyInBag[i] = buf[(head + i) % buf.length];
        }
        nq.alreadyGeneratedNumber = generationNumber;
        return nq;
    }

    public static PieceQueue createFromNetQueue(NetQueue nq) {
        if (nq.alreadyGeneratedNumber == 0) {
            return new PieceQueue(nq.seed, nq.bag);
        }
        ArrayList<Integer> list = new ArrayList<Integer>();
        for (byte b : nq.piecesAlreadyInBag) list.add((int) b);
        return new PieceQueue(nq.seed, nq.bag, list, nq.alreadyGeneratedNumber);
    }

    public static enum BagTypes {
        BAG_7(new byte[]{Piece.I, Piece.J, Piece.L, Piece.O, Piece.S, Piece.T, Piece.Z}),
        BAG_3MINO(new byte[]{Piece.I3, Piece.L3, Piece.I3, Piece.L3}),
        /** Wizard character passive: queue contains only J, L, S, T, Z pieces (implementation.md, Part 4). */
        BAG_WIZARD(new byte[]{Piece.J, Piece.L, Piece.S, Piece.T, Piece.Z});

        private byte[] pieces;
        private BagTypes(byte[] pieces) {
            this.pieces = pieces;
        }
        public byte[] get() {
            return pieces;
        }
    }
}
