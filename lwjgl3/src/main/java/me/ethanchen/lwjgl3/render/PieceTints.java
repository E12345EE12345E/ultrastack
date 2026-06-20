package me.ethanchen.lwjgl3.render;

import com.badlogic.gdx.graphics.Color;

import me.ethanchen.game.board.Tile;
import me.ethanchen.lwjgl3.settings.GameSettings;

/** Piece-type tint colors defined in HSV for easy tuning. */
public final class PieceTints {
    private static final Color[] COLORS = new Color[256];
    private static final float[] HUE    = new float[256];
    private static final float[] SAT    = new float[256];
    private static final float[] VALUE  = new float[256];

    // Per-piece runtime offsets (updated by applyColorOffsets)
    private static final float[] HUE_OFFSET      = new float[256];
    private static final float[] SAT_OFFSET      = new float[256];
    private static final float[] BG_VALUE_OFFSET = new float[256];

    // Default (unknown / unset types): white
    private static final float DEFAULT_H = 0f;
    private static final float DEFAULT_S = 0f;
    private static final float DEFAULT_V = 1f;

    // Value used when tinting tilebg.png behind tiles
    public static final float TILE_BG_V     = 0.75f;
    public static final float TILE_BG_V_ADD = 0.3f;

    /** Max additive RGB scale for glow at the farthest perceptual hue from {@link #PERCEPTUAL_REF_HUE}. */
    public static final float GLOW_BRIGHTNESS_ADD = 0.3f;

    private static final float PERCEPTUAL_REF_HUE      = 110f;
    private static final float PERCEPTUAL_HUE_DISTANCE_MAX = 180f;

    // Hue in degrees [0, 360); saturation and value in [0, 1]
    private static final float I_H = 180f, I_S = 1f, I_V = 1f;
    private static final float J_H = 240f, J_S = 1f, J_V = 1f;
    private static final float L_H = 30f,  L_S = 1f, L_V = 1f;
    private static final float O_H = 55f,  O_S = 1f, O_V = 1f;
    private static final float S_H = 110f, S_S = 1f, S_V = 1f;
    private static final float T_H = 280f, T_S = 1f, T_V = 1f;
    private static final float Z_H = 0f,   Z_S = 1f, Z_V = 1f;

    private static final float I3_H = 146f,  I3_S = 0.67f, I3_V = 0.55f;
    private static final float L3_H = 285f,  L3_S = 0.35f, L3_V = 1f;

    private static final float GARBAGE_H = 0f, GARBAGE_S = 0f, GARBAGE_V = 0.35f;

    private static final Color scratch  = new Color();
    private static final Color scratch2 = new Color();

    static {
        for (int i = 0; i < COLORS.length; i++) {
            setType(i, DEFAULT_H, DEFAULT_S, DEFAULT_V);
        }
        setType(Tile.I,       I_H,       I_S,       I_V);
        setType(Tile.J,       J_H,       J_S,       J_V);
        setType(Tile.L,       L_H,       L_S,       L_V);
        setType(Tile.O,       O_H,       O_S,       O_V);
        setType(Tile.S,       S_H,       S_S,       S_V);
        setType(Tile.T,       T_H,       T_S,       T_V);
        setType(Tile.Z,       Z_H,       Z_S,       Z_V);
        setType(Tile.I3,      I3_H,      I3_S,      I3_V);
        setType(Tile.L3,      L3_H,      L3_S,      L3_V);
        setType(Tile.GARBAGE, GARBAGE_H, GARBAGE_S, GARBAGE_V);
    }

    private PieceTints() {}

    // -------------------------------------------------------------------------
    // Public color accessors
    // -------------------------------------------------------------------------

    public static Color forType(byte type) {
        return COLORS[type & 0xFF];
    }

    /** Same hue and saturation as the piece type (with offsets), with perceptual bg-value adjustment. */
    public static Color forTileBackground(byte type) {
        int   index = type & 0xFF;
        float h     = adjustedHue(index);
        float s     = adjustedSat(index);
        float v     = clamp(tileBackgroundValue(h) + BG_VALUE_OFFSET[index]);
        scratch.fromHsv(h, s, v);
        scratch.a = 1f;
        return scratch;
    }

    /** Piece tint with perceptual brightness boost for additive glow rendering. */
    public static Color forGlow(byte type) {
        int   index = type & 0xFF;
        float h     = adjustedHue(index);
        float s     = adjustedSat(index);
        float v     = glowValue(h);
        scratch2.fromHsv(h, s, v);
        scratch2.a = 1f;
        return scratch2;
    }

    // -------------------------------------------------------------------------
    // Settings-driven offset application
    // -------------------------------------------------------------------------

    /**
     * Reads per-piece tweaks from settings and recomputes all affected COLORS[]
     * entries. Call once after loading settings, and again whenever the user
     * changes a color slider.
     */
    public static void applyColorOffsets(GameSettings.ColorTweaks t) {
        setOffsets(Tile.I,       t.I.hue,       t.I.sat,       t.I.bgValue);
        setOffsets(Tile.J,       t.J.hue,       t.J.sat,       t.J.bgValue);
        setOffsets(Tile.L,       t.L.hue,       t.L.sat,       t.L.bgValue);
        setOffsets(Tile.O,       t.O.hue,       t.O.sat,       t.O.bgValue);
        setOffsets(Tile.S,       t.S.hue,       t.S.sat,       t.S.bgValue);
        setOffsets(Tile.T,       t.T.hue,       t.T.sat,       t.T.bgValue);
        setOffsets(Tile.Z,       t.Z.hue,       t.Z.sat,       t.Z.bgValue);
        setOffsets(Tile.I3,      t.I3.hue,      t.I3.sat,      t.I3.bgValue);
        setOffsets(Tile.L3,      t.L3.hue,      t.L3.sat,      t.L3.bgValue);
        setOffsets(Tile.GARBAGE, t.garbage.hue, t.garbage.sat, t.garbage.bgValue);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static void setOffsets(int type, int hueInt, int satInt, int bgValueInt) {
        HUE_OFFSET[type]      = hueInt;                // degrees, applied directly
        SAT_OFFSET[type]      = satInt      / 100f;    // -0.20 to 0.0
        BG_VALUE_OFFSET[type] = bgValueInt  / 100f;    // -0.20 to 0.20
        recomputeColor(type);
    }

    private static void recomputeColor(int index) {
        float h = adjustedHue(index);
        float s = adjustedSat(index);
        float v = VALUE[index];
        COLORS[index].fromHsv(h, s, v);
        COLORS[index].a = 1f;
    }

    private static float adjustedHue(int index) {
        return wrapHue(HUE[index] + HUE_OFFSET[index]);
    }

    private static float adjustedSat(int index) {
        return clamp(SAT[index] + SAT_OFFSET[index]);
    }

    private static float hueDistanceFromReference(float hue) {
        float distTo110  = Math.abs(hue - PERCEPTUAL_REF_HUE);
        float distTo470  = Math.abs(hue - (PERCEPTUAL_REF_HUE + 360f));
        return Math.min(distTo110, distTo470);
    }

    private static float perceptualValueAdd(float hue, float maxAdd) {
        return (hueDistanceFromReference(hue) / PERCEPTUAL_HUE_DISTANCE_MAX) * maxAdd;
    }

    /** Brighter V for hues that read darker at equal saturation (distance from 110°). */
    private static float tileBackgroundValue(float hue) {
        return Math.min(1f, TILE_BG_V + perceptualValueAdd(hue, TILE_BG_V_ADD));
    }

    private static float glowValue(float hue) {
        return 1f + perceptualValueAdd(hue, GLOW_BRIGHTNESS_ADD);
    }

    private static void setType(int type, float h, float s, float v) {
        HUE[type]    = h;
        SAT[type]    = s;
        VALUE[type]  = v;
        COLORS[type] = hsv(h, s, v);
    }

    private static Color hsv(float h, float s, float v) {
        Color color = new Color();
        color.fromHsv(h, s, v);
        color.a = 1f;
        return color;
    }

    private static float wrapHue(float h) {
        h = h % 360f;
        if (h < 0f) h += 360f;
        return h;
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
