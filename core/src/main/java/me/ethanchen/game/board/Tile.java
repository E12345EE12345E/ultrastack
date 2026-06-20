package me.ethanchen.game.board;

public class Tile {
    public static final byte EMPTY = 0;

    // Types (tetrominoes)
    public static final byte I = 1; public static Tile I() { return new Tile(I); }
    public static final byte J = 2; public static Tile J() { return new Tile(J); }
    public static final byte L = 3; public static Tile L() { return new Tile(L); }
    public static final byte O = 4; public static Tile O() { return new Tile(O); }
    public static final byte S = 5; public static Tile S() { return new Tile(S); }
    public static final byte T = 6; public static Tile T() { return new Tile(T); }
    public static final byte Z = 7; public static Tile Z() { return new Tile(Z); }

    // Types (3-minoes)
    public static final byte I3 = 8; public static Tile I3() { return new Tile(I3); }
    public static final byte L3 = 9; public static Tile L3() { return new Tile(L3); }

    // Types (other)
    public static final byte GARBAGE = 10; public static Tile Garbage() { return new Tile(GARBAGE); }

    // Connection States
    public static final byte SINGLE_TILE = 0;
    
    // Object
    private byte type; // used for piece type (color)
    private byte connectionstate; // used for texture (is this a corner, straight line, etc)
    public Tile(byte type) {
        this(type, SINGLE_TILE);
    }
    public Tile(byte type, byte connectionstate) {
        this.type = type;
        this.connectionstate = connectionstate;
    }
    public Tile(int type) {
        this((byte)type);
    }
    public Tile(int type, int connectionstate) {
        this((byte)type, (byte)connectionstate);
    }

    public void set(byte type) {
        this.type = type;
    }

    public void set(byte type, byte connectionstate) {
        this.type = type;
        this.connectionstate = connectionstate;
    }

    public byte get() { return type; }
    public byte tex() { return connectionstate; }
}
