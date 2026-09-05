package me.ethanchen.server;

import java.util.Arrays;

/**
 * Mutable per-player bump/block tallies for a single run. Frozen into score/puzzle/PvE end-game
 * payloads (and thus {@code game_results.extra_json}) at game end.
 */
final class BumpStats {
    /** Mutual lateral bumps, indexed by player slot. */
    final int[] bumps;
    /** Hard drops blocked by a recently-moved piece, indexed by player slot. */
    final int[] blocks;
    /** Lateral moves cancelled by a non-mutual/stale blocker, indexed by player slot. */
    final int[] stationaryBumps;
    /** Hard drops blocked by a stale piece, indexed by player slot. */
    final int[] stationaryBlocks;

    BumpStats(int players) {
        bumps = new int[players];
        blocks = new int[players];
        stationaryBumps = new int[players];
        stationaryBlocks = new int[players];
    }

    private BumpStats(int[] bumps, int[] blocks, int[] stationaryBumps, int[] stationaryBlocks) {
        this.bumps = bumps;
        this.blocks = blocks;
        this.stationaryBumps = stationaryBumps;
        this.stationaryBlocks = stationaryBlocks;
    }

    void incrementBump(int playerA, int playerB) {
        if (playerA >= 0 && playerA < bumps.length) bumps[playerA]++;
        if (playerB >= 0 && playerB < bumps.length) bumps[playerB]++;
    }

    void incrementBlock(int droppedPlayerId) {
        if (droppedPlayerId >= 0 && droppedPlayerId < blocks.length) blocks[droppedPlayerId]++;
    }

    void incrementStationaryBump(int mover) {
        if (mover >= 0 && mover < stationaryBumps.length) stationaryBumps[mover]++;
    }

    void incrementStationaryBlock(int droppedPlayerId) {
        if (droppedPlayerId >= 0 && droppedPlayerId < stationaryBlocks.length) stationaryBlocks[droppedPlayerId]++;
    }

    BumpStats copy() {
        return new BumpStats(
                Arrays.copyOf(bumps, bumps.length),
                Arrays.copyOf(blocks, blocks.length),
                Arrays.copyOf(stationaryBumps, stationaryBumps.length),
                Arrays.copyOf(stationaryBlocks, stationaryBlocks.length));
    }
}
