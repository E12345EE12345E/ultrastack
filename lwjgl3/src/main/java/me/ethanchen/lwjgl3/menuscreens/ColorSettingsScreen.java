package me.ethanchen.lwjgl3.menuscreens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;

import me.ethanchen.game.board.Piece;
import me.ethanchen.game.board.Tile;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.ui.UIButton;
import me.ethanchen.lwjgl3.menuscreens.ui.UISlider;
import me.ethanchen.lwjgl3.menuscreens.ui.UIText;
import me.ethanchen.lwjgl3.render.BoardRenderer;
import me.ethanchen.lwjgl3.render.PieceTints;
import me.ethanchen.lwjgl3.settings.GameSettings;
import me.ethanchen.lwjgl3.settings.SettingsManager;

/**
 * Settings screen showing all 10 piece types in a 5x2 grid with HSV offset sliders.
 * Sliders modify offsets from the piece's default colour rather than absolute values.
 * Changes are only committed to disk when the Done button is pressed.
 */
public class ColorSettingsScreen extends MenuScreen {

    // -------------------------------------------------------------------------
    // Piece data
    // -------------------------------------------------------------------------

    private static final byte[] PIECE_TYPES = {
        Tile.I, Tile.J, Tile.L, Tile.O, Tile.S,
        Tile.T, Tile.Z, Tile.I3, Tile.L3, Tile.GARBAGE
    };

    private static final String[] PIECE_NAMES = {
        "I", "J", "L", "O", "S",
        "T", "Z", "I3", "L3", "Garbage"
    };

    // -------------------------------------------------------------------------
    // Layout constants
    // -------------------------------------------------------------------------

    private static final double[] COL_X = { 0.1, 0.3, 0.5, 0.7, 0.9 };

    private static final double SLIDER_W = 0.175;
    private static final double SLIDER_H = 0.065;

    /** Row-1 Y positions */
    private static final double R1_NAME   = 0.90;
    private static final double R1_HUE    = 0.73;
    private static final double R1_SAT    = 0.66;
    private static final double R1_BGVAL  = 0.59;

    /** Row-2 is shifted down by this amount */
    private static final double ROW_SHIFT = 0.41;

    private static final double R2_NAME   = R1_NAME  - ROW_SHIFT;
    private static final double R2_HUE    = R1_HUE   - ROW_SHIFT;
    private static final double R2_SAT    = R1_SAT   - ROW_SHIFT;
    private static final double R2_BGVAL  = R1_BGVAL - ROW_SHIFT;

    /** Relative-Y centre for piece tile previews (same coordinate system as buttons) */
    private static final float R1_PREVIEW_Y = 0.83f;
    private static final float R2_PREVIEW_Y = R1_PREVIEW_Y - (float) ROW_SHIFT;

    // -------------------------------------------------------------------------
    // Instance fields
    // -------------------------------------------------------------------------

    private final UISlider[] hueSliders   = new UISlider[10];
    private final UISlider[] satSliders   = new UISlider[10];
    private final UISlider[] bgValSliders = new UISlider[10];

    /** Live working copy; written to settings only on Done. */
    private final GameSettings.ColorTweaks tempTweaks = new GameSettings.ColorTweaks();

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public ColorSettingsScreen(ClientApp app) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());

        // Initialise temp copy from saved settings
        copyTweaks(app.getSettings().colors, tempTweaks);
        // Reflect temp in PieceTints so previews are correct from the start
        PieceTints.applyColorOffsets(tempTweaks);

        elements.add(new UIText(0.5, 0.96, "Color Settings", 2.5));

        GameSettings.PieceColorTweak[] tweakArr = tweakArray(tempTweaks);

        for (int i = 0; i < 10; i++) {
            final int idx = i;
            int    col  = i % 5;
            boolean row2 = i >= 5;

            double cx     = COL_X[col];
            double nameY  = row2 ? R2_NAME  : R1_NAME;
            double hueY   = row2 ? R2_HUE   : R1_HUE;
            double satY   = row2 ? R2_SAT   : R1_SAT;
            double bgValY = row2 ? R2_BGVAL : R1_BGVAL;

            elements.add(new UIText(cx, nameY, PIECE_NAMES[i], 1));

            hueSliders[i]   = new UISlider(cx, hueY,   SLIDER_W, SLIDER_H, "HUE",    -20, 20, tweakArr[i].hue);
            satSliders[i]   = new UISlider(cx, satY,   SLIDER_W, SLIDER_H, "SAT",    -20,  0, tweakArr[i].sat);
            bgValSliders[i] = new UISlider(cx, bgValY, SLIDER_W, SLIDER_H, "BG_VAL", -20, 20, tweakArr[i].bgValue);

            hueSliders[i].setOnChange(   () -> onSliderChanged(idx));
            satSliders[i].setOnChange(   () -> onSliderChanged(idx));
            bgValSliders[i].setOnChange( () -> onSliderChanged(idx));

            elements.add(hueSliders[i]);
            elements.add(satSliders[i]);
            elements.add(bgValSliders[i]);
        }

        elements.add(new UIButton(0.5, 0.07, 0.28, 0.07, "Done", this::saveAndExit));
    }

    // -------------------------------------------------------------------------
    // Slider callbacks
    // -------------------------------------------------------------------------

    private void onSliderChanged(int idx) {
        GameSettings.PieceColorTweak[] arr = tweakArray(tempTweaks);
        arr[idx].hue     = hueSliders[idx].getValue();
        arr[idx].sat     = satSliders[idx].getValue();
        arr[idx].bgValue = bgValSliders[idx].getValue();
        PieceTints.applyColorOffsets(tempTweaks);
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    private void saveAndExit() {
        copyTweaks(tempTweaks, app.getSettings().colors);
        SettingsManager.save(app.getSettings());
        app.switchMenu(new MainSettingsScreen(app));
    }

    @Override
    protected void onEscPressed() {
        PieceTints.applyColorOffsets(app.getSettings().colors);
        app.switchMenu(new MainSettingsScreen(app));
    }

    // -------------------------------------------------------------------------
    // Screen lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void update() {}

    @Override
    public void render() {
        elements.forEach(el -> el.render(shapes, sprites, font));
        renderPiecePreviews();
    }

    // -------------------------------------------------------------------------
    // Piece tile preview
    // -------------------------------------------------------------------------

    private void renderPiecePreviews() {
        float sw       = Gdx.graphics.getWidth();
        float sh       = Gdx.graphics.getHeight();
        float tileSize = sw * 0.025f;

        BoardRenderer renderer = BoardRenderer.getInstance();
        sprites.begin();

        for (int i = 0; i < 10; i++) {
            int     col         = i % 5;
            boolean row2        = i >= 5;
            float   previewRelY = row2 ? R2_PREVIEW_Y : R1_PREVIEW_Y;

            float colCenterX = (float) COL_X[col] * sw;
            float centerY    = previewRelY * sh;

            byte type = PIECE_TYPES[i];

            Vector2[] tiles;
            byte[]    states;
            Vector2   loc;

            if (type == Tile.GARBAGE) {
                // Garbage has no defaultPiece shape; render a single isolated tile.
                tiles  = new Vector2[]{ new Vector2(0, 0) };
                states = new byte[]{ Tile.SINGLE_TILE };
                loc    = new Vector2(0, 0);
            } else {
                Piece p = Piece.defaultPiece(type);
                tiles  = p.tiles;
                states = p.tileconnectionstates;
                loc    = p.location;
            }

            // Bounding box of world-space tile positions (loc + tile offset)
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
            float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (Vector2 t : tiles) {
                float wx = loc.x + t.x;
                float wy = loc.y + t.y;
                if (wx < minX) minX = wx;
                if (wx > maxX) maxX = wx;
                if (wy < minY) minY = wy;
                if (wy > maxY) maxY = wy;
            }

            float pieceW  = (maxX - minX + 1) * tileSize;
            float pieceH  = (maxY - minY + 1) * tileSize;
            float originX = colCenterX - pieceW * 0.5f - minX * tileSize;
            float originY = centerY    - pieceH * 0.5f - minY * tileSize;

            for (int j = 0; j < tiles.length; j++) {
                float tx = originX + (loc.x + tiles[j].x) * tileSize;
                float ty = originY + (loc.y + tiles[j].y) * tileSize;
                renderer.drawTileBgPreview(sprites, tx, ty, tileSize, type);
                renderer.drawTilePreview(  sprites, tx, ty, tileSize, type, states[j]);
            }
        }

        sprites.end();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the 10 per-piece tweaks in the same order as {@link #PIECE_TYPES}:
     * I, J, L, O, S, T, Z, I3, L3, garbage.
     */
    private static GameSettings.PieceColorTweak[] tweakArray(GameSettings.ColorTweaks t) {
        return new GameSettings.PieceColorTweak[] {
            t.I, t.J, t.L, t.O, t.S, t.T, t.Z, t.I3, t.L3, t.garbage
        };
    }

    private static void copyTweaks(GameSettings.ColorTweaks src, GameSettings.ColorTweaks dst) {
        copyTweak(src.I,       dst.I);
        copyTweak(src.J,       dst.J);
        copyTweak(src.L,       dst.L);
        copyTweak(src.O,       dst.O);
        copyTweak(src.S,       dst.S);
        copyTweak(src.T,       dst.T);
        copyTweak(src.Z,       dst.Z);
        copyTweak(src.I3,      dst.I3);
        copyTweak(src.L3,      dst.L3);
        copyTweak(src.garbage, dst.garbage);
    }

    private static void copyTweak(GameSettings.PieceColorTweak src, GameSettings.PieceColorTweak dst) {
        dst.hue     = src.hue;
        dst.sat     = src.sat;
        dst.bgValue = src.bgValue;
    }
}
