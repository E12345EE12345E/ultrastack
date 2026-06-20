package me.ethanchen.game.board;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

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

    private ArrayList<Integer> generateNextBag() {
        ArrayList<Integer> shuffleBag = new ArrayList<Integer>();
        for (byte b : bag.get()) shuffleBag.add((int)b);

        Collections.shuffle(shuffleBag, new Random(this.random.nextInt()));

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
        BAG_3MINO(new byte[]{Piece.I3, Piece.L3});

        private byte[] pieces;
        private BagTypes(byte[] pieces) {
            this.pieces = pieces;
        }
        public byte[] get() {
            return pieces;
        }
    }

    public static class NetQueue { // Only sent on init and desyncs
        public int seed;
        public BagTypes bag;
        public byte[] piecesAlreadyInBag;
        public int alreadyGeneratedNumber;
    }
}
