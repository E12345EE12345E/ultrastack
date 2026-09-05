package me.ethanchen.game.board;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

import me.ethanchen.network.dto.NetQueue;

public class PieceQueue {
    protected final ArrayList<Integer> pieceIDs;
    protected final int seed;
    private int generationNumber;
    private final Random random;
    protected final BagTypes bag;

    public PieceQueue(int seed, BagTypes bag) {
        this.seed = seed;
        this.bag = bag;
        this.generationNumber = 0;
        this.random = new Random(seed);
        this.pieceIDs = new ArrayList<Integer>();
    }

    public PieceQueue(int seed, BagTypes bag, ArrayList<Integer> piecesAlreadyInBag, int alreadyGeneratedNumber) {
        this(seed, bag);
        this.pieceIDs.addAll(piecesAlreadyInBag);
        while (this.generationNumber < alreadyGeneratedNumber) {
            this.random.nextInt();
            this.generationNumber++;
        }
    }

    public void refill() {
        while (this.pieceIDs.size() < bag.get().length) {
            this.pieceIDs.addAll(generateNextBag());
        }
    }

    public byte takeNext() {
        refill();
        return (byte)(int)this.pieceIDs.remove(0);
    }

    /** Returns the upcoming piece at {@code index} (0 = next to spawn) without consuming it. */
    public byte peek(int index) {
        if (index < 0) return 0;
        ensureAvailable(index + 1);
        return (byte) (int) pieceIDs.get(index);
    }

    /** Returns the next {@code count} upcoming piece types without consuming them. */
    public byte[] peekMany(int count) {
        int n = Math.max(0, count);
        byte[] out = new byte[n];
        if (n == 0) return out;
        ensureAvailable(n);
        for (int i = 0; i < n; i++) {
            out[i] = (byte) (int) pieceIDs.get(i);
        }
        return out;
    }

    private void ensureAvailable(int count) {
        refill();
        while (pieceIDs.size() < count) {
            pieceIDs.addAll(generateNextBag());
        }
    }

    private static final byte[] BAG_3MINO_EXTRAS = {
        Piece.J, Piece.L, Piece.S, Piece.Z, Piece.O
    };

    private ArrayList<Integer> generateNextBag() {
        ArrayList<Integer> shuffleBag = new ArrayList<Integer>();
        for (byte b : bag.get()) shuffleBag.add((int)b);

        // One nextInt() per bag keeps NetQueue reconstruction in sync (see ctor that burns RNG).
        Random bagRng = new Random(this.random.nextInt());
        if (bag == BagTypes.BAG_3MINO) {
            shuffleBag.add((int) BAG_3MINO_EXTRAS[bagRng.nextInt(BAG_3MINO_EXTRAS.length)]);
        }
        Collections.shuffle(shuffleBag, bagRng);

        this.generationNumber++;
        return shuffleBag;
    }

    public NetQueue convertToNetQueue() {
        NetQueue nq = new NetQueue();
        nq.seed = seed;
        nq.bag = bag;
        nq.piecesAlreadyInBag = new byte[pieceIDs.size()];
        for (int i=0; i<pieceIDs.size(); i++) nq.piecesAlreadyInBag[i] = (byte)(int)pieceIDs.get(i);
        nq.alreadyGeneratedNumber = generationNumber;
        return nq;
    }

    public static PieceQueue createFromNetQueue(NetQueue nq) {
        if (nq.alreadyGeneratedNumber == 0) {
            return new PieceQueue(nq.seed, nq.bag);
        }
        ArrayList<Integer> list = new ArrayList<Integer>();
        for (byte b : nq.piecesAlreadyInBag) list.add((int)b);
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
