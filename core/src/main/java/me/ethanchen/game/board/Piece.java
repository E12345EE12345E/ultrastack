package me.ethanchen.game.board;

import com.badlogic.gdx.math.Vector2;

public class Piece {
    // Types (tetrominoes)
    public static final byte I = 1; public static Piece I() { return defaultPiece(I); }
    public static final byte J = 2; public static Piece J() { return defaultPiece(J); }
    public static final byte L = 3; public static Piece L() { return defaultPiece(L); }
    public static final byte O = 4; public static Piece O() { return defaultPiece(O); }
    public static final byte S = 5; public static Piece S() { return defaultPiece(S); }
    public static final byte T = 6; public static Piece T() { return defaultPiece(T); }
    public static final byte Z = 7; public static Piece Z() { return defaultPiece(Z); }

    // Types (3-minoes)
    public static final byte I3 = 8; public static Piece I3() { return defaultPiece(I3); }
    public static final byte L3 = 9; public static Piece L3() { return defaultPiece(L3); }

    // SRS wall kick offsets (8 transitions x 5 tests each)
    public static final Vector2[] WALL_KICKS_JLSTZ = {
        new Vector2(0, 0), new Vector2(-1, 0), new Vector2(-1, 1), new Vector2(0, -2), new Vector2(-1, -2), // 0->R
        new Vector2(0, 0), new Vector2(1, 0), new Vector2(1, -1), new Vector2(0, 2), new Vector2(1, 2),       // R->0
        new Vector2(0, 0), new Vector2(1, 0), new Vector2(1, -1), new Vector2(0, 2), new Vector2(1, 2),       // R->2
        new Vector2(0, 0), new Vector2(-1, 0), new Vector2(-1, 1), new Vector2(0, -2), new Vector2(-1, -2),   // 2->R
        new Vector2(0, 0), new Vector2(1, 0), new Vector2(1, 1), new Vector2(0, -2), new Vector2(1, -2),       // 2->L
        new Vector2(0, 0), new Vector2(-1, 0), new Vector2(-1, -1), new Vector2(0, 2), new Vector2(-1, 2),   // L->2
        new Vector2(0, 0), new Vector2(-1, 0), new Vector2(-1, -1), new Vector2(0, 2), new Vector2(-1, 2),   // L->0
        new Vector2(0, 0), new Vector2(1, 0), new Vector2(1, 1), new Vector2(0, -2), new Vector2(1, -2),     // 0->L
    };
    public static final Vector2[] WALL_KICKS_I = {
        new Vector2(0, 0), new Vector2(1, 0), new Vector2(-2, 0), new Vector2(-2, -1), new Vector2(1, 2),    // 0->R
        new Vector2(0, 0), new Vector2(-1, 0), new Vector2(2, 0), new Vector2(-1, -2), new Vector2(2, 1),    // R->0
        new Vector2(0, 0), new Vector2(-1, 0), new Vector2(2, 0), new Vector2(-1, 2), new Vector2(2, -1),    // R->2
        new Vector2(0, 0), new Vector2(-2, 0), new Vector2(1, 0), new Vector2(-2, 1), new Vector2(1, -2),    // 2->R
        new Vector2(0, 0), new Vector2(2, 0), new Vector2(-1, 0), new Vector2(2, 1), new Vector2(-1, -2),    // 2->L
        new Vector2(0, 0), new Vector2(1, 0), new Vector2(-2, 0), new Vector2(1, 2), new Vector2(-2, -1),    // L->2
        new Vector2(0, 0), new Vector2(1, 0), new Vector2(-2, 0), new Vector2(1, -2), new Vector2(-2, 1),    // L->0
        new Vector2(0, 0), new Vector2(-1, 0), new Vector2(2, 0), new Vector2(2, -1), new Vector2(-1, 2),    // 0->L
    };

    // SRS+ 180 rotation wall kicks — stride 5, indexed by fromRotation (0..3)
    public static final Vector2[] WALL_KICKS_180_JLSTZ = {
        new Vector2(0, 1), new Vector2(1, 1), new Vector2(-1, 1), new Vector2(1, 0), new Vector2(-1, 0),     // 0->2
        new Vector2(1, 0), new Vector2(1, 2), new Vector2(1, 1), new Vector2(0, 2), new Vector2(0, 1),       // 1->3
        new Vector2(0, -1), new Vector2(-1, -1), new Vector2(1, -1), new Vector2(-1, 0), new Vector2(1, 0),  // 2->0
        new Vector2(-1, 0), new Vector2(-1, 2), new Vector2(-1, 1), new Vector2(0, 2), new Vector2(0, 1),    // 3->1
    };
    // SRS+ 180 rotation wall kicks — stride 1, indexed by fromRotation (0..3)
    public static final Vector2[] WALL_KICKS_180_I = {
        new Vector2(0, 1),   // 0->2
        new Vector2(1, 0),   // 1->3
        new Vector2(0, -1),  // 2->0
        new Vector2(-1, 0),  // 3->1
    };

    // Object
    public byte type;
    public Vector2 location;
    public Vector2[] tiles;
    public byte[] tileconnectionstates;
    public byte rotation;
    public boolean lastMoveWasRotation = false;
    public Piece(byte type) {
        this.type = type;
        this.rotation = 0;
    }
    public Piece(int type) {
        this((byte)type);
    }

    public void rotateCW() {
        this.rotation++;
        if (this.rotation >= 4) this.rotation = 0;
        for (Vector2 v : tiles) {
            float x = v.x;
            float y = v.y;
            v.x = y;
            v.y = -x;
        }
    }

    public void rotateCCW() {
        this.rotation--;
        if (this.rotation < 0) this.rotation = 3;
        for (Vector2 v : tiles) {
            float x = v.x;
            float y = v.y;
            v.x = -y;
            v.y = x;
        }
    }
    
    public void rotate180() {
        this.rotation += 2;
        if (this.rotation >= 4) this.rotation -= 4;
        for (Vector2 v : tiles) {
            float x = v.x;
            float y = v.y;
            v.x = -x;
            v.y = -y;
        }
    }

    public void rotateTexCW() {
        for (int i=0; i<tileconnectionstates.length; i++) {
            switch (tileconnectionstates[i]) {
                case 1:
                case 2:
                case 3:
                case 5:
                case 7:
                case 8:
                case 9:
                case 11:
                case 12:
                case 13:
                    tileconnectionstates[i]++;
                    break;
                case 4:
                case 10:
                case 14:
                    tileconnectionstates[i] -= 3;
                    break;
                case 6:
                    tileconnectionstates[i]--;
                    break;
                // case 0 and 15 don't rotate
            }
        }
    }

    public void rotateTexCCW() {
        for (int i=0; i<tileconnectionstates.length; i++) {
            switch (tileconnectionstates[i]) {
                case 2:
                case 3:
                case 4:
                case 6:
                case 8:
                case 9:
                case 10:
                case 12:
                case 13:
                case 14:
                    tileconnectionstates[i]--;
                    break;
                case 1:
                case 7:
                case 11:
                    tileconnectionstates[i] += 3;
                    break;
                case 5:
                    tileconnectionstates[i]++;
                    break;
                // case 0 and 15 don't rotate
            }
        }
    }

    public NetPiece convertToNetPiece() {
        NetPiece np = new NetPiece();
        np.type = type;
        np.doubledlocationx = (byte) Math.floor(location.x * 2);
        np.doubledlocationy = (byte) Math.floor(location.y * 2);
        np.rotation = rotation;
        return np;
    }

    public void updateFromNetPiece(NetPiece p) {
        if (p.type != type) return; // only works if same type
        location.set(p.doubledlocationx*0.5f, p.doubledlocationy*0.5f);
        if (p.rotation != rotation) {
            int a = p.rotation - rotation;
            if (a < 0) a += 4;
            switch (a) {
                case 1:
                    rotateCW();
                    rotateTexCW();
                    break;
                case 2:
                    rotate180();
                    rotateTexCW(); rotateTexCW();
                    break;
                case 3:
                    rotateCCW();
                    rotateTexCCW();
                    break;
            }
        }
    }

    public static Piece createFromNetPiece(NetPiece p) {
        Piece retval = defaultPiece(p.type);
        retval.location = new Vector2(p.doubledlocationx*0.5f, p.doubledlocationy*0.5f);
        switch (p.rotation) {
            case 1:
                retval.rotateCW();
                retval.rotateTexCW();
                break;
            case 2:
                retval.rotate180();
                retval.rotateTexCW(); retval.rotateTexCW();
                break;
            case 3:
                retval.rotateCCW();
                retval.rotateTexCCW();
                break;
        }
        return retval;
    }

    public static Piece defaultPiece(byte type) {
        Piece piece = new Piece(type);

        switch (type) {
            case I:
                piece.tiles = new Vector2[]{new Vector2(-1.5f, 0.5f), new Vector2(-0.5f, 0.5f), new Vector2(0.5f, 0.5f), new Vector2(1.5f, 0.5f)};
                piece.tileconnectionstates = new byte[]{1,5,5,3};
                break;
            case J:
                piece.tiles = new Vector2[]{new Vector2(-1, 1), new Vector2(-1, 0), new Vector2(0, 0), new Vector2(1, 0)};
                piece.tileconnectionstates = new byte[]{2,7,5,3};
                break;
            case L:
                piece.tiles = new Vector2[]{new Vector2(-1, 0), new Vector2(0, 0), new Vector2(1, 0), new Vector2(1, 1)};
                piece.tileconnectionstates = new byte[]{1,5,10,2};
                break;
            case O:
                piece.tiles = new Vector2[]{new Vector2(0.5f, 0.5f), new Vector2(0.5f, -0.5f), new Vector2(-0.5f, 0.5f), new Vector2(-0.5f, -0.5f)};
                piece.tileconnectionstates = new byte[]{9,10,8,7};
                break;
            case S:
                piece.tiles = new Vector2[]{new Vector2(-1, 0), new Vector2(0, 0), new Vector2(0, 1), new Vector2(1, 1)};
                piece.tileconnectionstates = new byte[]{1,10,8,3};
                break;
            case T:
                piece.tiles = new Vector2[]{new Vector2(-1, 0), new Vector2(0, 0), new Vector2(1, 0), new Vector2(0, 1)};
                piece.tileconnectionstates = new byte[]{1,14,3,2};
                break;
            case Z:
                piece.tiles = new Vector2[]{new Vector2(-1, 1), new Vector2(0, 1), new Vector2(0, 0), new Vector2(1, 0)};
                piece.tileconnectionstates = new byte[]{1,9,7,3};
                break;
            case I3:
                piece.tiles = new Vector2[]{new Vector2(-1, 0), new Vector2(0, 0), new Vector2(1, 0)};
                piece.tileconnectionstates = new byte[]{1,5,3};
                break;
            case L3:
                piece.tiles = new Vector2[]{new Vector2(0, 1), new Vector2(0, 0), new Vector2(1, 0)};
                piece.tileconnectionstates = new byte[]{2,7,3};
                break;
        }
        switch (type) {
            case I:
            case O:
                piece.location = new Vector2(0.5f, 0.5f);
                break;
            case I3:
            case L3:
                piece.location = new Vector2(0, 1);
                break;
            default:
                piece.location = new Vector2(0, 0);
                break;
        }

        return piece;
    }

    public static class NetPiece { // smaller class to be sent over network
        public byte type;
        public byte doubledlocationx; // I and O pieces have centers on 0.5, so location is doubled to become integer and halved on packet arrival
        public byte doubledlocationy;
        public byte rotation;
    }
}
