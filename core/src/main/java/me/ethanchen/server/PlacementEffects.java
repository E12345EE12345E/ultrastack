package me.ethanchen.server;

import java.util.ArrayList;
import java.util.Arrays;

import me.ethanchen.game.board.LineClearResult;
import me.ethanchen.game.board.SpinType;
import me.ethanchen.network.packets.s2c.BumpSoundBroadcast;
import me.ethanchen.network.packets.s2c.HoldSoundBroadcast;
import me.ethanchen.network.packets.s2c.NetParticle;
import me.ethanchen.network.packets.s2c.ParticleSpawner;
import me.ethanchen.network.packets.s2c.PlacementSoundBroadcast;

/**
 * Accumulates per-tick particle events and sound broadcasts for delivery during the next
 * {@link GameRoomContext#sendNetUpdates()} call. Extracted from {@link ServerGame} to keep the
 * particle/sound queuing responsibility separate from scoring and game-state management.
 */
class PlacementEffects {

    final ArrayList<NetParticle> pendingParticles = new ArrayList<>();
    final ArrayList<ParticleSpawner> pendingSpawners = new ArrayList<>();
    final ArrayList<PlacementSoundBroadcast> pendingPlacementSounds = new ArrayList<>();
    final ArrayList<HoldSoundBroadcast> pendingHoldSounds = new ArrayList<>();
    final ArrayList<BumpSoundBroadcast> pendingBumpSounds = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Queueing
    // -------------------------------------------------------------------------

    /**
     * Builds and queues a {@link PlacementSoundBroadcast} for the given placement result.
     *
     * @param result     the placement that just occurred
     * @param priorCombo the combo counter captured <em>before</em> applyClearToCounters ran
     */
    void queuePlacementSound(LineClearResult result, int priorCombo) {
        PlacementSoundBroadcast psb = new PlacementSoundBroadcast();
        psb.playerId = (byte) result.playerId;

        int lines = result.numClearedRows();
        switch (result.spinType) {
            case T_SPIN:
            case T_SPIN_MINI:
                psb.spinType = PlacementSoundBroadcast.SPIN_TSPIN;
                break;
            case ALL_SPIN:
            case SMALL_SPIN:
                psb.spinType = PlacementSoundBroadcast.SPIN_ALL_SPIN;
                break;
            default:
                psb.spinType = (lines == 4) ? PlacementSoundBroadcast.SPIN_TETRIS : PlacementSoundBroadcast.SPIN_NONE;
                break;
        }

        psb.combo = (lines > 0) ? (byte) priorCombo : (byte) -1;
        pendingPlacementSounds.add(psb);
    }

    /**
     * Translates a {@link LineClearResult} into compact {@link ParticleSpawner} events and any
     * remaining individual {@link NetParticle} events.
     *
     * @param result     the placement result to expand
     * @param boardWidth column count of the board (used for tile-break spawner arrays)
     */
    void queueResultParticles(LineClearResult result, int boardWidth) {
        // Hard-drop flash: one spawner encodes the whole piece
        if (!result.placedCells.isEmpty()) {
            ParticleSpawner ps = new ParticleSpawner();
            ps.spawnerType = ParticleSpawner.TYPE_HARD_DROP;
            ps.boardIndex = 0;
            ps.pieceType = result.pieceType;
            ps.doubledX = (byte) Math.floor(result.restingCenterX * 2);
            ps.doubledY = (byte) Math.floor(result.restingCenterY * 2);
            ps.pieceRotation = result.pieceRotation;
            pendingSpawners.add(ps);
        }

        // Line-clear tile-break: one spawner per cleared row
        if (result.clearedRows != null && result.clearedRows.length > 0) {
            for (int row : result.clearedRows) {
                byte[] tileIds = new byte[boardWidth];
                Arrays.fill(tileIds, (byte) -1);
                for (int[] cell : result.clearedCells) {
                    if (cell[1] == row) {
                        tileIds[cell[0]] = (byte) cell[2];
                    }
                }
                ParticleSpawner ps = new ParticleSpawner();
                ps.spawnerType = ParticleSpawner.TYPE_LINE_CLEAR;
                ps.boardIndex = 0;
                ps.lineY = (byte) row;
                ps.tileIds = tileIds;
                pendingSpawners.add(ps);
            }
        }

        // Broken cells: individual NetParticles
        for (int[] cell : result.brokenCells) {
            NetParticle np = new NetParticle();
            np.boardIndex = 0;
            np.kind = NetParticle.KIND_TILE_BREAK;
            np.tileType = (byte) cell[2];
            np.x = cell[0];
            np.y = cell[1];
            pendingParticles.add(np);
        }
    }

    /** Queues a {@link HoldSoundBroadcast}. */
    void addHoldSound(byte playerId, boolean success) {
        HoldSoundBroadcast hsb = new HoldSoundBroadcast();
        hsb.playerId = playerId;
        hsb.success = success;
        pendingHoldSounds.add(hsb);
    }

    /** Queues a {@link BumpSoundBroadcast}. */
    void addBumpSound(byte playerId, byte otherPlayerId, boolean blocked) {
        BumpSoundBroadcast bsb = new BumpSoundBroadcast();
        bsb.playerId = playerId;
        bsb.otherPlayerId = otherPlayerId;
        bsb.blocked = blocked;
        pendingBumpSounds.add(bsb);
    }

    // -------------------------------------------------------------------------
    // Draining (called by GameRoom.sendNetUpdates)
    // -------------------------------------------------------------------------

    ArrayList<NetParticle> getAndClearPendingParticles() {
        if (pendingParticles.isEmpty()) return null;
        ArrayList<NetParticle> copy = new ArrayList<>(pendingParticles);
        pendingParticles.clear();
        return copy;
    }

    ArrayList<ParticleSpawner> getAndClearPendingSpawners() {
        if (pendingSpawners.isEmpty()) return null;
        ArrayList<ParticleSpawner> copy = new ArrayList<>(pendingSpawners);
        pendingSpawners.clear();
        return copy;
    }

    ArrayList<PlacementSoundBroadcast> getAndClearPendingPlacementSounds() {
        if (pendingPlacementSounds.isEmpty()) return null;
        ArrayList<PlacementSoundBroadcast> copy = new ArrayList<>(pendingPlacementSounds);
        pendingPlacementSounds.clear();
        return copy;
    }

    ArrayList<HoldSoundBroadcast> getAndClearPendingHoldSounds() {
        if (pendingHoldSounds.isEmpty()) return null;
        ArrayList<HoldSoundBroadcast> copy = new ArrayList<>(pendingHoldSounds);
        pendingHoldSounds.clear();
        return copy;
    }

    ArrayList<BumpSoundBroadcast> getAndClearPendingBumpSounds() {
        if (pendingBumpSounds.isEmpty()) return null;
        ArrayList<BumpSoundBroadcast> copy = new ArrayList<>(pendingBumpSounds);
        pendingBumpSounds.clear();
        return copy;
    }
}
