package me.ethanchen.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.board.Board;
import me.ethanchen.game.board.LineClearResult;
import me.ethanchen.game.board.MoveType;
import me.ethanchen.game.board.Piece;
import me.ethanchen.game.board.PieceQueue;
import me.ethanchen.network.dto.NetQueue;

/**
 * Seeded replay through {@link GameHandler}. The hash must stay identical across compute
 * refactors that are not allowed to change gameplay outcomes.
 */
class GameHandlerGoldenReplayTest {

    private static final long GOLDEN_HASH = -9151509709685595180L;

    private static final MoveType[] SCRIPT = {
            MoveType.ROTATE_CW, MoveType.LEFT, MoveType.LEFT, MoveType.HARD_DROP,
            MoveType.RIGHT, MoveType.RIGHT, MoveType.SOFT_DROP, MoveType.HARD_DROP,
            MoveType.ROTATE_CCW, MoveType.LEFT, MoveType.HARD_DROP,
            MoveType.HOLD, MoveType.RIGHT, MoveType.HARD_DROP,
            MoveType.ROTATE_180, MoveType.SOFT_DROP, MoveType.SOFT_DROP, MoveType.HARD_DROP,
            MoveType.LEFT, MoveType.LEFT, MoveType.ROTATE_CW, MoveType.HARD_DROP,
            MoveType.RIGHT, MoveType.HOLD, MoveType.LEFT, MoveType.HARD_DROP
    };

    @Test
    void seededReplayHashIsStable() {
        long first = runReplay();
        assertEquals(first, runReplay(), "replay is not deterministic");
        assertEquals(GOLDEN_HASH, first,
                "Gameplay hash changed — a refactor altered lock / clear / queue outcomes");
    }

    static long runReplay() {
        GameHandler game = new GameHandler(1);
        game.init(GameMode.MULTIPLAYER_SCORE, 1_000_000);
        Board board = game.getBoards().get(0);
        board.setPieceQueue(0, new PieceQueue(42, PieceQueue.BagTypes.BAG_7));
        game.update(1_000_000);

        long hash = 0xcbf29ce484222325L;
        int script = 0;
        for (int t = 0; t < 2000; t++) {
            game.update(16);
            if (t % 4 == 0) {
                board.applyMove(0, SCRIPT[script % SCRIPT.length]);
                script++;
            }
            List<LineClearResult> results = game.getAndClearPendingLockResults();
            for (LineClearResult r : results) {
                if (r.placed) game.applyClearToCounters(r);
                hash = mixResult(hash, r);
            }
        }
        hash = mixBoard(hash, board);
        hash = mix(hash, game.getCombo(0));
        hash = mix(hash, game.getB2b(0));
        hash = mix(hash, game.getGravityTickCounter(0));
        return hash;
    }

    private static long mixBoard(long hash, Board board) {
        for (int y = 0; y < board.bh(); y++) {
            for (int x = 0; x < board.bw(); x++) {
                hash = mix(hash, board.tileTypeAt(x, y));
                hash = mix(hash, board.tileTexAt(x, y));
            }
        }
        for (Piece p : board.getActivePieces()) {
            hash = mix(hash, p.type);
            hash = mix(hash, p.rotation);
            hash = mix(hash, Float.floatToIntBits(p.location.x));
            hash = mix(hash, Float.floatToIntBits(p.location.y));
        }
        NetQueue q = board.getPieceQueue(0).convertToNetQueue();
        hash = mix(hash, q.alreadyGeneratedNumber);
        if (q.piecesAlreadyInBag != null) {
            for (byte b : q.piecesAlreadyInBag) hash = mix(hash, b);
        }
        return hash;
    }

    private static long mixResult(long hash, LineClearResult r) {
        hash = mix(hash, r.placed ? 1 : 0);
        hash = mix(hash, r.pieceType);
        hash = mix(hash, r.spinType.ordinal());
        hash = mix(hash, r.allClear ? 1 : 0);
        hash = mix(hash, r.fallingClear ? 1 : 0);
        hash = mix(hash, r.numClearedRows());
        if (r.clearedRows != null) {
            for (int row : r.clearedRows) hash = mix(hash, row);
        }
        return hash;
    }

    private static long mix(long hash, long value) {
        hash ^= value;
        hash *= 0x100000001b3L;
        return hash;
    }
}
