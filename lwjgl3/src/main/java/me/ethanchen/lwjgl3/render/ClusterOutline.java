package me.ethanchen.lwjgl3.render;

import java.util.Arrays;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;

import me.ethanchen.game.board.Board;
import me.ethanchen.game.board.Tile;

/**
 * Client-only silhouette stroke around contiguous locked tiles. Occupancy is 4-connected;
 * thickness scales with cluster size up to {@link #MAX_CLUSTER} tiles.
 */
final class ClusterOutline {

    static final int MAX_CLUSTER = 10;
    /** Outline thickness as a fraction of {@code tileSize} at cluster size 1. */
    static final float T_MIN = 0.08f;
    /** Outline thickness as a fraction of {@code tileSize} at cluster size {@link #MAX_CLUSTER}+. */
    static final float T_MAX = 0.12f;
    private static final float T_CAP = 0.45f;

    private final ShapeRenderer shapes = new ShapeRenderer();

    /** 0 = empty; 1..N = component id. Sized to the largest board seen. */
    private int[][] groupId;
    private int[] groupSize;
    private int[] stack;
    private int bufW;
    private int bufH;

    void draw(Board board, float originX, float originY, float tileSize, Matrix4 projection) {
        if (board == null || tileSize <= 0f) return;
        int w = board.bw();
        int h = board.bh();
        if (w <= 0 || h <= 0) return;

        if (label(board.getBoard(), w, h) == 0) return;

        shapes.setProjectionMatrix(projection);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Color.WHITE);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int id = groupId[y][x];
                if (id == 0) continue;
                float t = thickness(groupSize[id], tileSize);
                if (t <= 0f) continue;
                float sx = originX + x * tileSize;
                float sy = originY + y * tileSize;
                boolean north = same(id, x, y + 1, w, h);
                boolean south = same(id, x, y - 1, w, h);
                boolean west  = same(id, x - 1, y, w, h);
                boolean east  = same(id, x + 1, y, w, h);
                if (!north) shapes.rect(sx, sy + tileSize - t, tileSize, t);
                if (!south) shapes.rect(sx, sy, tileSize, t);
                if (!west)  shapes.rect(sx, sy, t, tileSize);
                if (!east)  shapes.rect(sx + tileSize - t, sy, t, tileSize);
                // Concave corners: N/S and E/W strips meet at a point, leaving a t×t gap
                // inside the cell that owns both orthogonal neighbors (L-elbows, holes).
                if (east && north && !same(id, x + 1, y + 1, w, h)) {
                    shapes.rect(sx + tileSize - t, sy + tileSize - t, t, t);
                }
                if (west && north && !same(id, x - 1, y + 1, w, h)) {
                    shapes.rect(sx, sy + tileSize - t, t, t);
                }
                if (east && south && !same(id, x + 1, y - 1, w, h)) {
                    shapes.rect(sx + tileSize - t, sy, t, t);
                }
                if (west && south && !same(id, x - 1, y - 1, w, h)) {
                    shapes.rect(sx, sy, t, t);
                }
            }
        }

        shapes.end();
    }

    void dispose() {
        shapes.dispose();
    }

    /**
     * Thickness in pixels. Scales from a hairline at size 1 toward {@link #T_MAX} of a tile at
     * size {@link #MAX_CLUSTER}+, capped so a single cell still has an interior.
     */
    static float thickness(int count, float tileSize) {
        if (count <= 0 || tileSize <= 0f) return 0f;
        float u = Math.min(count, MAX_CLUSTER) / (float) MAX_CLUSTER;
        float t = tileSize * (T_MIN + (T_MAX - T_MIN) * u);
        float cap = tileSize * T_CAP;
        return t > cap ? cap : t;
    }

    /** Flood-fills 4-connected occupancy into {@link #groupId} / {@link #groupSize}. */
    int label(Tile[][] tiles, int w, int h) {
        ensureBuffers(w, h);
        for (int y = 0; y < h; y++) {
            Arrays.fill(groupId[y], 0, w, 0);
        }
        int nextId = 1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (groupId[y][x] != 0) continue;
                if (!occupied(tiles, x, y)) continue;
                groupSize[nextId] = flood(tiles, w, h, x, y, nextId);
                nextId++;
            }
        }
        return nextId - 1;
    }

    private int flood(Tile[][] tiles, int w, int h, int startX, int startY, int id) {
        int sp = 0;
        groupId[startY][startX] = id;
        stack[sp++] = pack(startX, startY);
        int count = 0;
        while (sp > 0) {
            int packed = stack[--sp];
            int x = packed & 0xFFFF;
            int y = packed >>> 16;
            count++;
            sp = tryPush(tiles, w, h, x + 1, y, id, sp);
            sp = tryPush(tiles, w, h, x - 1, y, id, sp);
            sp = tryPush(tiles, w, h, x, y + 1, id, sp);
            sp = tryPush(tiles, w, h, x, y - 1, id, sp);
        }
        return count;
    }

    private int tryPush(Tile[][] tiles, int w, int h, int x, int y, int id, int sp) {
        if (x < 0 || x >= w || y < 0 || y >= h) return sp;
        if (groupId[y][x] != 0) return sp;
        if (!occupied(tiles, x, y)) return sp;
        groupId[y][x] = id;
        stack[sp] = pack(x, y);
        return sp + 1;
    }

    private static int pack(int x, int y) {
        return x | (y << 16);
    }

    private boolean same(int id, int x, int y, int w, int h) {
        if (x < 0 || x >= w || y < 0 || y >= h) return false;
        return groupId[y][x] == id;
    }

    private static boolean occupied(Tile[][] tiles, int x, int y) {
        Tile tile = tiles[y][x];
        return tile != null && tile.get() != Tile.EMPTY;
    }

    private void ensureBuffers(int w, int h) {
        if (groupId != null && bufW >= w && bufH >= h) return;
        int nw = Math.max(w, bufW);
        int nh = Math.max(h, bufH);
        groupId = new int[nh][nw];
        groupSize = new int[nw * nh + 1];
        stack = new int[nw * nh];
        bufW = nw;
        bufH = nh;
    }
}
