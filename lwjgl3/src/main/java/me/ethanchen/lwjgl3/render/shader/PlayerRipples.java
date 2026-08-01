package me.ethanchen.lwjgl3.render.shader;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;

import me.ethanchen.game.board.Board;
import me.ethanchen.game.board.Piece;

/**
 * Owns one {@link RippleCircleRenderer} and tracks a per-player-slot ripple circle drawn under
 * the board's active pieces.
 *
 * <p>Locally controlled slots spring smoothly toward the centroid of their active piece and grow
 * from a small, faint circle to their full radius/opacity over {@link #GROW_SECONDS} once the
 * game starts. Non-local (remote) slots simply teleport to their piece's centroid every frame and
 * stay small and very faint.
 *
 * <p>Every ripple fades in at its spawn position when first created. When a placement occurs
 * (signalled by {@link #poof(int)}), the ripple fades out in place, disappears briefly, then
 * teleports to the next piece's spawn/position and fades back in — never sliding across the
 * board. A local, large-jump fallback in {@link #update} triggers the same poof sequence in case
 * a cosmetic UDP poof signal is lost.
 */
public class PlayerRipples {

    private static final float RADIUS_SMALL = 2.5f;
    private static final float RADIUS_LARGE = 3.0f;
    private static final float THICKNESS_SMALL = 1.2f;
    private static final float THICKNESS_LARGE = 1.6f;
    private static final float RIPPLE_INTENSITY = 0.2f;

    private static final float OPACITY_REMOTE_MIN = 0.08f;
    private static final float OPACITY_REMOTE_MAX = 0.10f;
    private static final float OPACITY_LOCAL_MIN = 0.20f;
    private static final float OPACITY_LOCAL_MAX = 0.30f;

    /** Applies to any slot (local or remote) whose piece is in the blocked-cycling state. */
    private static final float RADIUS_BLOCKED = 3.5f;
    private static final float THICKNESS_BLOCKED = 2.0f;
    private static final float OPACITY_BLOCKED_MIN = 0.06f;
    private static final float OPACITY_BLOCKED_MAX = 0.08f;

    private static final float GROW_SECONDS = 1.0f;
    private static final float SPAWN_FADE_SECONDS = 1.0f;

    private static final float POOF_OUT_SECONDS = 0.08f;
    private static final float POOF_HIDDEN_SECONDS = 0.10f;
    private static final float RESPAWN_FADE_SECONDS = 0.15f;

    private static final float TELEPORT_DISTANCE = 5.0f;

    private static final float SPRING_STIFFNESS = 400f;
    private static final float SPRING_DAMPING = 2f * (float) Math.sqrt(SPRING_STIFFNESS);

    private static final Color[] SLOT_COLORS = {
            Color.RED, Color.GREEN, Color.ORANGE, Color.PURPLE
    };

    /**
     * Per-slot-color opacity multiplier, applied on top of every opacity value computed in
     * {@link #draw}. Green/orange/purple read as visually brighter than red at equal alpha, so
     * they are rendered at half opacity to look balanced against it.
     */
    private static final float[] SLOT_OPACITY_MULT = { 1.0f, 0.5f, 0.5f, 0.5f };

    private enum Phase { SPAWN_FADE_IN, ACTIVE, POOF_OUT, POOF_HIDDEN }

    private final RippleCircleRenderer renderer = new RippleCircleRenderer();
    private final boolean[] isLocal;
    private final Slot[] slots;
    private float clock = 0f;

    private static final class Slot {
        float x, y;
        float vx, vy;
        float alpha;
        float growT;
        Phase phase = Phase.SPAWN_FADE_IN;
        float phaseTimer;
        float poofFromAlpha;
        float fadeInDuration = SPAWN_FADE_SECONDS;
        boolean blocked;
    }

    public PlayerRipples(Board board, boolean[] isLocalSlot) {
        int count = board.getSpawnPositions().length;
        this.isLocal = new boolean[count];
        for (int i = 0; i < count; i++) {
            this.isLocal[i] = isLocalSlot != null && i < isLocalSlot.length && isLocalSlot[i];
        }
        this.slots = new Slot[count];
        for (int i = 0; i < count; i++) {
            Slot s = new Slot();
            Vector2 spawn = spawnCenter(board, i);
            s.x = spawn.x;
            s.y = spawn.y;
            s.alpha = 0f;
            s.growT = 0f;
            slots[i] = s;
        }
    }

    /** True when the slot's active piece is cycling in the blocked-spawn state. */
    private static boolean isBlocked(Board board, int slot) {
        if (slot >= board.getActivePieces().size()) return false;
        Piece piece = board.getActivePieces().get(slot);
        return piece != null && piece.isBlockedFromSpawning;
    }

    /**
     * Resolves the ripple's follow target for a slot, honoring the blocked-cycling override:
     * blocked slots (local or remote) always target their raw spawn tile rather than the piece
     * centroid, since the cycling piece isn't a meaningful position to chase.
     */
    private static Vector2 computeTarget(Board board, int slot) {
        if (isBlocked(board, slot)) {
            return spawnTile(board, slot);
        }
        return targetCenter(board, slot);
    }

    /**
     * Board-space centroid of a slot's active piece, or its spawn point if it has none yet.
     * Returned in the same cell-index convention that {@link RippleCircleRenderer#draw} expects
     * for {@code boardX}/{@code boardY} (i.e. the renderer itself adds the +0.5 tile-center
     * offset internally; callers must not pre-add it).
     */
    private static Vector2 targetCenter(Board board, int slot) {
        if (slot < board.getActivePieces().size()) {
            Piece piece = board.getActivePieces().get(slot);
            if (piece != null && piece.location != null && piece.tiles != null && piece.tiles.length > 0) {
                float cx = 0f, cy = 0f;
                for (Vector2 tile : piece.tiles) {
                    cx += piece.location.x + tile.x;
                    cy += piece.location.y + tile.y;
                }
                cx /= piece.tiles.length;
                cy /= piece.tiles.length;
                return new Vector2(cx, cy);
            }
        }
        return spawnCenter(board, slot);
    }

    /** Rough bounding-box center of a not-yet-spawned piece at its spawn anchor. */
    private static Vector2 spawnCenter(Board board, int slot) {
        Vector2 spawn = board.getSpawnPos(slot);
        return new Vector2(spawn.x + 1.5f, spawn.y + 1.0f);
    }

    /** The raw spawn position tile for a slot, with no bounding-box centering offset. */
    private static Vector2 spawnTile(Board board, int slot) {
        Vector2 spawn = board.getSpawnPos(slot);
        return new Vector2(spawn.x, spawn.y);
    }

    /**
     * Signals that slot {@code slot}'s piece was just placed: the ripple poofs out in place,
     * disappears briefly, then reappears (teleported, not slid) at the next piece's position.
     */
    public void poof(int slot) {
        if (slot < 0 || slot >= slots.length) return;
        Slot s = slots[slot];
        if (s.phase == Phase.POOF_OUT || s.phase == Phase.POOF_HIDDEN) return;
        s.phase = Phase.POOF_OUT;
        s.phaseTimer = 0f;
        s.poofFromAlpha = s.alpha;
        s.vx = 0f;
        s.vy = 0f;
    }

    public void update(Board board, float dt, boolean gameStarted) {
        clock += dt;
        for (int i = 0; i < slots.length; i++) {
            Slot s = slots[i];
            Vector2 target = computeTarget(board, i);
            s.blocked = isBlocked(board, i);

            switch (s.phase) {
                case SPAWN_FADE_IN: {
                    s.phaseTimer += dt;
                    s.alpha = Math.min(1f, s.phaseTimer / s.fadeInDuration);
                    if (isLocal[i]) {
                        springTowards(s, target, dt);
                    } else {
                        s.x = target.x;
                        s.y = target.y;
                    }
                    if (s.phaseTimer >= s.fadeInDuration) {
                        s.phase = Phase.ACTIVE;
                    }
                    break;
                }
                case ACTIVE: {
                    if (isLocal[i]) {
                        springTowards(s, target, dt);
                        float dx = target.x - s.x;
                        float dy = target.y - s.y;
                        if (Math.sqrt(dx * dx + dy * dy) > TELEPORT_DISTANCE) {
                            poof(i);
                            continue;
                        }
                    } else {
                        s.x = target.x;
                        s.y = target.y;
                    }
                    s.alpha = 1f;
                    if (gameStarted && isLocal[i] && s.growT < 1f) {
                        s.growT = Math.min(1f, s.growT + dt / GROW_SECONDS);
                    }
                    break;
                }
                case POOF_OUT: {
                    s.phaseTimer += dt;
                    float t = Math.min(1f, s.phaseTimer / POOF_OUT_SECONDS);
                    s.alpha = s.poofFromAlpha * (1f - t);
                    if (s.phaseTimer >= POOF_OUT_SECONDS) {
                        s.phase = Phase.POOF_HIDDEN;
                        s.phaseTimer = 0f;
                        s.alpha = 0f;
                    }
                    break;
                }
                case POOF_HIDDEN: {
                    s.phaseTimer += dt;
                    if (s.phaseTimer >= POOF_HIDDEN_SECONDS) {
                        Vector2 respawn = computeTarget(board, i);
                        s.x = respawn.x;
                        s.y = respawn.y;
                        s.vx = 0f;
                        s.vy = 0f;
                        s.phase = Phase.SPAWN_FADE_IN;
                        s.phaseTimer = 0f;
                        s.alpha = 0f;
                        // Reuse the fade-in machinery but with the shorter respawn duration.
                        s.fadeInDuration = RESPAWN_FADE_SECONDS;
                    }
                    break;
                }
            }
        }
    }

    private void springTowards(Slot s, Vector2 target, float dt) {
        float dx = target.x - s.x;
        float dy = target.y - s.y;
        s.vx += (SPRING_STIFFNESS * dx - SPRING_DAMPING * s.vx) * dt;
        s.vy += (SPRING_STIFFNESS * dy - SPRING_DAMPING * s.vy) * dt;
        s.x += s.vx * dt;
        s.y += s.vy * dt;
    }

    public void draw(float originX, float originY, float tileSize) {
        for (int i = 0; i < slots.length; i++) {
            Slot s = slots[i];
            if (s.phase == Phase.POOF_HIDDEN) continue;
            if (s.alpha <= 0f) continue;

            float radius, thickness, minOpacity, maxOpacity;
            if (s.blocked) {
                radius = RADIUS_BLOCKED;
                thickness = THICKNESS_BLOCKED;
                minOpacity = OPACITY_BLOCKED_MIN;
                maxOpacity = OPACITY_BLOCKED_MAX;
            } else {
                radius = lerp(RADIUS_SMALL, RADIUS_LARGE, isLocal[i] ? s.growT : 0f);
                thickness = lerp(THICKNESS_SMALL, THICKNESS_LARGE, isLocal[i] ? s.growT : 0f);
                minOpacity = lerp(OPACITY_REMOTE_MIN, OPACITY_LOCAL_MIN, isLocal[i] ? s.growT : 0f);
                maxOpacity = lerp(OPACITY_REMOTE_MAX, OPACITY_LOCAL_MAX, isLocal[i] ? s.growT : 0f);
            }

            Color color = SLOT_COLORS[i % SLOT_COLORS.length];
            float opacityMult = SLOT_OPACITY_MULT[i % SLOT_OPACITY_MULT.length];
            RippleShaderColor colorData = new RippleShaderColor(
                    new Color[] { color }, minOpacity * opacityMult, maxOpacity * opacityMult,
                    RippleShaderColor.ColorMode.ANGULAR, 0f);

            renderer.draw(originX, originY, tileSize, s.x, s.y, radius,
                    1.0f, 1.0f, thickness, RIPPLE_INTENSITY, colorData, clock, s.alpha);
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public void reloadShader() {
        renderer.reloadShader();
    }

    public void dispose() {
        renderer.dispose();
    }
}
