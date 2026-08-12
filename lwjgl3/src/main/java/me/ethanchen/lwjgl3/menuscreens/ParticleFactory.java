package me.ethanchen.lwjgl3.menuscreens;

import java.util.List;
import java.util.Random;

import com.badlogic.gdx.graphics.Color;

import me.ethanchen.game.board.Piece;
import me.ethanchen.lwjgl3.render.Particle;
import me.ethanchen.lwjgl3.render.PieceTints;
import me.ethanchen.network.dto.NetPiece;
import me.ethanchen.network.packets.s2c.NetParticle;
import me.ethanchen.network.packets.s2c.ParticleSpawner;

import com.badlogic.gdx.math.Vector2;

/**
 * Converts compact server-side particle descriptors ({@link ParticleSpawner} and
 * {@link NetParticle}) into local {@link Particle} objects that the client renders each frame.
 * Extracted from {@link GameScreen} to isolate the server-event → rendering-object translation.
 *
 * <p>All methods are stateless and accept an output list and a {@link Random} instance so they
 * can be used from any context without holding their own mutable state.
 */
final class ParticleFactory {

    private ParticleFactory() {}

    /**
     * Expands a compact {@link ParticleSpawner} into one or more local {@link Particle} objects
     * and appends them to {@code out}.
     *
     * <p>TYPE_LINE_CLEAR — emits one TILE_BREAK burst per non-(-1) entry in
     * {@link ParticleSpawner#tileIds}.
     *
     * <p>TYPE_HARD_DROP_CELLS — emits one FLASH particle per {@code (cellXs[i], cellYs[i])} pair.
     */
    static void expandSpawner(ParticleSpawner ps, List<Particle> out, Random rng) {
        if (ps.spawnerType == ParticleSpawner.TYPE_LINE_CLEAR) {
            if (ps.tileIds == null) return;
            for (int x = 0; x < ps.tileIds.length; x++) {
                if (ps.tileIds[x] == -1) continue;
                NetParticle tileBreak = new NetParticle();
                tileBreak.boardIndex = ps.boardIndex;
                tileBreak.kind = NetParticle.KIND_TILE_BREAK;
                tileBreak.tileType = ps.tileIds[x];
                tileBreak.x = x;
                tileBreak.y = ps.lineY;
                expandNetParticle(tileBreak, out, rng);
            }
        } else if (ps.spawnerType == ParticleSpawner.TYPE_HARD_DROP_CELLS) {
            if (ps.cellXs == null || ps.cellYs == null) return;
            int n = Math.min(ps.cellXs.length, ps.cellYs.length);
            for (int i = 0; i < n; i++) {
                NetParticle flash = new NetParticle();
                flash.boardIndex = ps.boardIndex;
                flash.kind = NetParticle.KIND_FLASH;
                flash.x = ps.cellXs[i];
                flash.y = ps.cellYs[i];
                expandNetParticle(flash, out, rng);
            }
        }
    }

    /**
     * Reconstructs the placed piece from its compact placement fields and emits one FLASH
     * particle per mino cell. Used for the hard-drop flash effect carried by
     * {@link me.ethanchen.network.dto.HardDropEffect}.
     */
    static void expandHardDropFlash(byte pieceType, byte doubledX, byte doubledY, byte rotation,
                                     byte boardIndex, List<Particle> out, Random rng) {
        NetPiece netPiece = new NetPiece();
        netPiece.type = pieceType;
        netPiece.doubledlocationx = doubledX;
        netPiece.doubledlocationy = doubledY;
        netPiece.rotation = rotation;
        Piece piece = Piece.createFromNetPiece(netPiece);
        for (Vector2 tile : piece.tiles) {
            int cx = (int) Math.floor(piece.location.x + tile.x);
            int cy = (int) Math.floor(piece.location.y + tile.y);
            NetParticle flash = new NetParticle();
            flash.boardIndex = boardIndex;
            flash.kind = NetParticle.KIND_FLASH;
            flash.tileType = pieceType;
            flash.x = cx;
            flash.y = cy;
            expandNetParticle(flash, out, rng);
        }
    }

    /**
     * Converts one {@link NetParticle} spawn event into one or more local {@link Particle}
     * objects and appends them to {@code out}.
     *
     * <ul>
     *   <li>FLASH — a single white square, lifetime ~0.12 s.
     *   <li>TILE_BREAK — 4–6 small colored shards with random outward velocities, lifetime ~0.5 s.
     *   <li>POPUP_SCORE — floating "+N" text launched upward from the piece center.
     *   <li>POPUP_SCORE_MULTIPLIER — floating bonus-label line at constant speed.
     * </ul>
     */
    static void expandNetParticle(NetParticle np, List<Particle> out, Random rng) {
        if (np.kind == NetParticle.KIND_FLASH) {
            Particle flash = new Particle();
            flash.kind = Particle.Kind.FLASH;
            flash.x = np.x + 0.5f;
            flash.y = np.y + 0.5f;
            flash.vx = 0;
            flash.vy = 0;
            flash.r = 1f;
            flash.g = 1f;
            flash.b = 1f;
            flash.size = 1.0f;
            flash.lifetime = 0.12f;
            out.add(flash);
        } else if (np.kind == NetParticle.KIND_TILE_BREAK) {
            Color tint = PieceTints.forType(np.tileType);
            int count = 4 + rng.nextInt(3);
            for (int i = 0; i < count; i++) {
                Particle shard = new Particle();
                shard.kind = Particle.Kind.TILE_BREAK;
                shard.x = np.x + rng.nextFloat();
                shard.y = np.y + rng.nextFloat();
                float angle = rng.nextFloat() * (float)(Math.PI * 2);
                float speed = 2f + rng.nextFloat() * 4f;
                shard.vx = (float) Math.cos(angle) * speed;
                shard.vy = (float) Math.sin(angle) * speed;
                shard.r = tint.r;
                shard.g = tint.g;
                shard.b = tint.b;
                shard.size = 0.18f + rng.nextFloat() * 0.14f;
                shard.lifetime = 0.35f + rng.nextFloat() * 0.25f;
                out.add(shard);
            }
        } else if (np.kind == NetParticle.KIND_POPUP_SCORE) {
            Particle pop = new Particle();
            pop.kind = Particle.Kind.POPUP_SCORE;
            pop.x = np.x;
            pop.y = np.y + 1f;
            pop.vx = 0f;
            pop.vy = 3f;
            pop.r = 1f; pop.g = 1f; pop.b = 1f;
            pop.value = np.value;
            pop.lifetime = 1.1f;
            out.add(pop);
        } else if (np.kind == NetParticle.KIND_POPUP_SCORE_MULTIPLIER) {
            Particle pop = new Particle();
            pop.kind = Particle.Kind.POPUP_SCORE_MULTIPLIER;
            pop.x = np.x;
            pop.y = np.y + 1.5f;
            pop.vx = 0f;
            pop.vy = 0f;
            pop.r = 1f; pop.g = 1f; pop.b = 1f;
            pop.value = np.value;
            pop.bonuses = new boolean[4];
            for (int bit = 0; bit < 4; bit++) {
                pop.bonuses[bit] = (np.value & (1 << bit)) != 0;
            }
            pop.lifetime = 1.2f;
            out.add(pop);
        }
    }
}
