package me.ethanchen.lwjgl3.render;

import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import me.ethanchen.game.board.Board;
import me.ethanchen.game.board.FallingColumn;
import me.ethanchen.game.board.Piece;
import me.ethanchen.game.board.Tile;
import me.ethanchen.lwjgl3.render.shader.GlowRenderer;



/**
 * Renders a {@link Board}: locked tiles, active pieces, grid outline, and optional per-piece glow.
 *
 * <p>The glow pipeline is managed by the contained {@link GlowRenderer}; the HUD boxes
 * (hold / timer) are drawn by {@link BoardHudRenderer}.
 *
 * <p>Call {@link #dispose()} when done.
 */
public class BoardRenderer {
    private static BoardRenderer instance;

    private static final int TILE_SHEET_TILE_SIZE = 16;

    // Forwarded to GlowRenderer for backwards-compatible public access
    /** @see GlowRenderer#GLOW_BLUR_RADIUS */
    public static float GLOW_BLUR_RADIUS  = GlowRenderer.GLOW_BLUR_RADIUS;
    /** @see GlowRenderer#GLOW_BLUR_PASSES */
    public static int   GLOW_BLUR_PASSES  = GlowRenderer.GLOW_BLUR_PASSES;

    // Tile textures – package-private so BoardHudRenderer can draw held pieces
    final Texture         tileSheet;
    final Texture         tileBackground;
    final TextureRegion[] tileRegions;

    private static final Color SHADOW_GRAY   = new Color(0.6f, 0.6f, 0.6f, 0.5f);
    private static final Color BLOCKED_GRAY  = new Color(PieceTints.GRAYSCALE_VALUE, PieceTints.GRAYSCALE_VALUE, PieceTints.GRAYSCALE_VALUE, 1f);
    private static final Color BLOCKED_WHITE = new Color(1f, 1f, 1f, 1f);
    /** Fall-trigger / piece-trigger tiles: override piece HSV with solid white. */
    private static final Color FALL_TRIGGER_WHITE = new Color(1f, 1f, 1f, 1f);
    private static final Color FALL_TRIGGER_BG    = new Color(0.8f, 0.8f, 0.8f, 1f);

    private final GlowRenderer glowRenderer;

    public GlowRenderer getGlowRenderer() {
        return glowRenderer;
    }


    public static BoardRenderer getInstance() {
        if (instance == null) instance = new BoardRenderer();
        return instance;
    }

    /** Disposes the singleton instance (if created) and clears it so a fresh one is built on next use. */
    public static void disposeInstance() {
        if (instance != null) {
            instance.dispose();
            instance = null;
        }
    }

    private BoardRenderer() {
        tileSheet = new Texture(Gdx.files.internal("tilesheetdefault.png"));
        tileSheet.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        tileBackground = new Texture(Gdx.files.internal("tilebg.png"));
        tileBackground.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        tileRegions = new TextureRegion[16];
        for (int i = 0; i < tileRegions.length; i++) {
            tileRegions[i] = new TextureRegion(tileSheet, i * TILE_SHEET_TILE_SIZE, 0,
                    TILE_SHEET_TILE_SIZE, TILE_SHEET_TILE_SIZE);
        }
        glowRenderer = new GlowRenderer();
    }

    // -------------------------------------------------------------------------
    // Layout helpers
    // -------------------------------------------------------------------------

    /** Largest tile size that fits the board within {@code maxFraction} of the screen. */
    public static float computeTileSize(Board board, float maxFraction) {
        float maxW = Gdx.graphics.getWidth()  * maxFraction / board.bw();
        float maxH = Gdx.graphics.getHeight() * maxFraction / board.bh();
        return Math.min(maxW, maxH);
    }

    /** X coordinate that centers the board horizontally on screen. */
    public static float centeredOriginX(Board board, float tileSize) {
        return (Gdx.graphics.getWidth() - board.bw() * tileSize) / 2f;
    }

    /** Y coordinate that centers the board vertically on screen. */
    public static float centeredOriginY(Board board, float tileSize) {
        return (Gdx.graphics.getHeight() - board.bh() * tileSize) / 2f;
    }

    // -------------------------------------------------------------------------
    // Public draw calls
    // -------------------------------------------------------------------------

    /**
     * Draws all non-empty locked tiles and active pieces.
     * {@code glowStrengths} sets per-active-piece glow intensity in [0, 1]; {@code null} disables glow.
     * Caller must NOT have an open SpriteBatch or ShapeRenderer begin/end around this call.
     */
    public void drawBoard(Board board, float originX, float originY, float tileSize,
                          SpriteBatch sprites, float[] glowStrengths) {
        drawBoard(board, originX, originY, tileSize, sprites, glowStrengths, null, 0f, true, null, 0f);
    }

    /**
     * Like {@link #drawBoard(Board, float, float, float, SpriteBatch, float[])} but also draws
     * per-piece drop shadows. {@code shadows} may be {@code null} to skip shadow rendering.
     */
    public void drawBoard(Board board, float originX, float originY, float tileSize,
                          SpriteBatch sprites, float[] glowStrengths, Board.ShadowInfo[] shadows) {
        drawBoard(board, originX, originY, tileSize, sprites, glowStrengths, shadows, 0f, true, null, 0f);
    }

    /**
     * Full drawBoard overload with blocked-piece tinting and active-piece suppression.
     *
     * @param blockedWhiteAmt         0 = full gray for blocked pieces; 1 = full white.
     * @param drawActivePieces        When false, active pieces and glow are not drawn.
     * @param isLocalPlayer           Per-piece flags; {@code null} or shorter arrays treat missing
     *                                indices as non-local. When null and grayscaleAmt &gt; 0, all
     *                                pieces stay full colour (spectator / disabled).
     * @param otherPlayerGrayscaleAmt 0 = full color for other players; 1 = fully grayscale.
     */
    public void drawBoard(Board board, float originX, float originY, float tileSize,
                          SpriteBatch sprites, float[] glowStrengths, Board.ShadowInfo[] shadows,
                          float blockedWhiteAmt, boolean drawActivePieces,
                          boolean[] isLocalPlayer, float otherPlayerGrayscaleAmt) {
        if (drawActivePieces) drawGlow(board, originX, originY, tileSize, glowStrengths);
        sprites.begin();
        drawLockedTiles(board, originX, originY, tileSize, sprites);
        drawFallingColumns(board, originX, originY, tileSize, sprites);
        if (drawActivePieces) {
            drawShadowPieces(board, shadows, originX, originY, tileSize, sprites,
                    isLocalPlayer, otherPlayerGrayscaleAmt);
            drawActivePiecesWithBlocked(board, originX, originY, tileSize, sprites, blockedWhiteAmt);
        }
        sprites.setColor(Color.WHITE);
        sprites.end();
    }

    /**
     * Draws the glow halo for active pieces.
     * {@code glowStrengths} sets per-active-piece glow intensity in [0, 2]; {@code null} disables glow.
     * Caller must NOT have an open SpriteBatch or ShapeRenderer begin/end around this call.
     */
    public void drawGlow(Board board, float originX, float originY, float tileSize,
                         float[] glowStrengths) {
        glowRenderer.draw(board, originX, originY, tileSize, glowStrengths);
    }

    public void drawGlow(Board board, float originX, float originY, float tileSize,
                         float[] glowStrengths, float deltaTime) {
        glowRenderer.draw(board, originX, originY, tileSize, glowStrengths, deltaTime);
    }


    /**
     * Draws the grid outline for every allowed cell.
     * Caller must NOT have an open ShapeRenderer begin/end around this call.
     */
    public void drawBoardGrid(Board board, float originX, float originY, float tileSize,
                              ShapeRenderer shapes) {
        boolean[][] allowed = board.getAllowedTiles();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Color.GRAY.r, Color.GRAY.g, Color.GRAY.b, 0.5f);
        for (int y = 0; y < board.bh(); y++) {
            for (int x = 0; x < board.bw(); x++) {
                if (!allowed[y][x]) continue;
                shapes.rect(originX + x * tileSize, originY + y * tileSize, tileSize, tileSize);
            }
        }
        shapes.end();
    }

    /**
     * Draws all live particles.
     * Each particle's board-space position is converted to screen pixels using the same
     * {@code originX/Y} and {@code tileSize} passed to {@link #drawBoard}.
     * Caller must NOT have an open SpriteBatch or ShapeRenderer begin/end around this call.
     */
    public void drawParticles(List<Particle> particles, float originX, float originY,
                              float tileSize, ShapeRenderer shapes) {
        if (particles == null || particles.isEmpty()) return;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Particle p : particles) {
            if (p.isDead()) continue;
            if (p.kind == Particle.Kind.POPUP_SCORE || p.kind == Particle.Kind.POPUP_SCORE_MULTIPLIER) continue;
            float alpha = p.alpha();
            shapes.setColor(p.r, p.g, p.b, alpha);
            float px = originX + p.x * tileSize;
            float py = originY + p.y * tileSize;
            float sz = p.size * tileSize;
            shapes.rect(px - sz * 0.5f, py - sz * 0.5f, sz, sz);
        }
        shapes.end();
    }

    /**
     * Draws a translucent overlay over all {@code allowedTiles=true} cells in the given column.
     * Used to highlight the repeat-column penalty zone in score mode.
     * Caller must NOT have an open SpriteBatch or ShapeRenderer begin/end around this call.
     */
    public void drawColumnHighlight(Board board, float originX, float originY, float tileSize,
                                    ShapeRenderer shapes, int column, float r, float g, float bv, float a) {
        if (column < 0 || column >= board.bw()) return;
        boolean[][] allowed = board.getAllowedTiles();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(r, g, bv, a);
        for (int y = 0; y < board.bh(); y++) {
            if (!allowed[y][column]) continue;
            shapes.rect(originX + column * tileSize, originY + y * tileSize, tileSize, tileSize);
        }
        shapes.end();
    }

    private static final Color LIME_GREEN = new Color(0f, 1f, 0f, 1f);

    private static final String[] BONUS_PCTS   = {"125%", "120%", "150%", "200%"};
    private static final String[] BONUS_LABELS = {" - B2B clear",
                                                   " - Avoiding repeated column",
                                                   " - Different player in combo",
                                                   " - Glow bonus"};

    /**
     * Draws floating score text ({@link Particle.Kind#POPUP_SCORE}) and bonus multiplier
     * text ({@link Particle.Kind#POPUP_SCORE_MULTIPLIER}) for all matching particles.
     * Caller must NOT have an open SpriteBatch or ShapeRenderer begin/end around this call.
     */
    public void drawTextParticles(List<Particle> particles, float originX, float originY,
                                  float tileSize, SpriteBatch sprites, BitmapFont font) {
        if (particles == null || particles.isEmpty()) return;

        GlyphLayout layout = new GlyphLayout();
        float savedScaleX = font.getScaleX();
        float savedScaleY = font.getScaleY();
        font.getData().setScale(1f);
        float unscaledLH = font.getData().lineHeight;
        float scaleAdj = 15f / unscaledLH;
        float fontScale = 1.75f * scaleAdj * (Gdx.graphics.getHeight() / 640f);
        font.getData().setScale(fontScale);

        sprites.begin();
        for (Particle p : particles) {
            if (p.isDead()) continue;
            float alpha = p.alpha();
            float px = originX + p.x * tileSize;
            float py = originY + p.y * tileSize;

            if (p.kind == Particle.Kind.POPUP_SCORE) {
                String text = "+" + p.value;
                layout.setText(font, text);
                font.setColor(1f, 1f, 1f, alpha);
                font.draw(sprites, text, px - layout.width * 0.5f, py + layout.height * 0.5f);

            } else if (p.kind == Particle.Kind.POPUP_SCORE_MULTIPLIER && p.bonuses != null) {
                float lineHeight = font.getData().lineHeight;
                int numLines = 0;
                for (boolean b : p.bonuses) if (b) numLines++;
                float totalH = numLines * lineHeight;
                float curY = py + totalH * 0.5f;

                for (int bit = 0; bit < 4; bit++) {
                    if (!p.bonuses[bit]) continue;
                    String pct   = BONUS_PCTS[bit];
                    String label = BONUS_LABELS[bit];
                    layout.setText(font, pct);
                    float pctW = layout.width;
                    layout.setText(font, pct + label);
                    float totalW = layout.width;
                    float startX = px - totalW * 0.5f;

                    font.setColor(LIME_GREEN.r, LIME_GREEN.g, LIME_GREEN.b, alpha);
                    font.draw(sprites, pct, startX, curY);

                    font.setColor(1f, 1f, 1f, alpha);
                    font.draw(sprites, label, startX + pctW, curY);

                    curY -= lineHeight;
                }
            }
        }
        sprites.end();

        font.getData().setScale(savedScaleX, savedScaleY);
    }

    // -------------------------------------------------------------------------
    // HUD boxes (hold / timers) — delegated to BoardHudRenderer
    // -------------------------------------------------------------------------

    /**
     * Draws the shared hold box to the left of the board.
     * Caller must NOT have an open SpriteBatch or ShapeRenderer begin/end around this call.
     */
    public void drawHoldBox(byte heldType, boolean available,
                            float x, float y, float boxSize, float tileSize,
                            ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font) {
        BoardHudRenderer.drawHoldBox(heldType, available, x, y, boxSize, tileSize,
                shapes, sprites, font, this);
    }

    /**
     * Draws a combined countdown timer + score box (score mode).
     * Caller must NOT have an open SpriteBatch or ShapeRenderer begin/end around this call.
     */
    public void drawTimerBox(long endTargetMs, long score,
                             float x, float y, float boxSize, float tileSize,
                             ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font) {
        BoardHudRenderer.drawTimerBox(endTargetMs, score, x, y, boxSize, tileSize,
                shapes, sprites, font);
    }

    /**
     * Draws a count-up timer box (puzzle mode): "TIME" label and MM:SS elapsed.
     * Caller must NOT have an open SpriteBatch or ShapeRenderer begin/end around this call.
     */
    public void drawCountUpTimerBox(long elapsedMs,
                                    float x, float y, float boxSize, float tileSize,
                                    ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font) {
        BoardHudRenderer.drawCountUpTimerBox(elapsedMs, x, y, boxSize, tileSize,
                shapes, sprites, font);
    }

    public void dispose() {
        tileSheet.dispose();
        tileBackground.dispose();
        glowRenderer.dispose();
        if (instance == this) instance = null;
    }

    // -------------------------------------------------------------------------
    // Tile rendering helpers
    // -------------------------------------------------------------------------

    private void drawShadowPieces(Board board, Board.ShadowInfo[] shadows,
                                  float originX, float originY, float tileSize,
                                  SpriteBatch sprites,
                                  boolean[] isLocalPlayer, float otherPlayerGrayscaleAmt) {
        if (shadows == null) return;
        float grayscaleAmt = Math.max(0f, Math.min(1f, otherPlayerGrayscaleAmt));
        for (int i = 0; i < shadows.length && i < board.getActivePieces().size(); i++) {
            Board.ShadowInfo shadow = shadows[i];
            if (shadow == null) continue;
            Piece piece = board.getActivePieces().get(i);
            if (piece.tiles == null || piece.location == null || piece.type == Tile.EMPTY) continue;
            if (piece.isBlockedFromSpawning) continue;

            if (Math.abs(shadow.locationX - piece.location.x) < 0.01f
                    && Math.abs(shadow.locationY - piece.location.y) < 0.01f) continue;

            Color baseColor;
            boolean local = isLocalPlayer != null && i < isLocalPlayer.length && isLocalPlayer[i];
            boolean otherPlayer = isLocalPlayer != null && !local;
            if (shadow.wouldPlace) {
                // Other players' shadows are more transparent; local stays more opaque.
                float alpha = otherPlayer ? 0.25f : 0.75f;
                if (piece.fallTrigger) {
                    baseColor = new Color(FALL_TRIGGER_WHITE.r, FALL_TRIGGER_WHITE.g,
                            FALL_TRIGGER_WHITE.b, alpha);
                } else {
                    // When isLocalPlayer is null, treat as "no grayscale" (legacy / spectator).
                    float colorAmt = otherPlayer ? 1f - grayscaleAmt : 1f;
                    Color c = PieceTints.blendGrayscale(piece.type, colorAmt, false);
                    baseColor = new Color(c.r, c.g, c.b, alpha);
                }
            } else {
                baseColor = SHADOW_GRAY;
            }

            sprites.setColor(baseColor);
            for (int j = 0; j < piece.tiles.length; j++) {
                float sx = originX + (shadow.locationX + piece.tiles[j].x) * tileSize;
                float sy = originY + (shadow.locationY + piece.tiles[j].y) * tileSize;
                byte connection = (piece.tileconnectionstates != null && j < piece.tileconnectionstates.length)
                        ? piece.tileconnectionstates[j] : Tile.SINGLE_TILE;
                sprites.draw(tileRegions[connection & 0xF], sx, sy, tileSize, tileSize);
            }
        }
    }

    private void drawLockedTiles(Board board, float originX, float originY, float tileSize,
                                 SpriteBatch sprites) {
        Tile[][] tiles = board.getBoard();
        for (int y = 0; y < board.bh(); y++) {
            for (int x = 0; x < board.bw(); x++) {
                Tile tile = tiles[y][x];
                if (tile == null || tile.get() == Tile.EMPTY) continue;
                float sx = originX + x * tileSize;
                float sy = originY + y * tileSize;
                drawTileBackground(sprites, sx, sy, tileSize, tile.get());
                drawTile(sprites, sx, sy, tileSize, tile.get(), tile.tex());
            }
        }
    }

    /** Draws airborne falling columns at their interpolated sub-tile Y positions. */
    private void drawFallingColumns(Board board, float originX, float originY, float tileSize,
                                    SpriteBatch sprites) {
        for (FallingColumn col : board.getFallingColumns()) {
            if (col.types == null) continue;
            for (int i = 0; i < col.types.length; i++) {
                if (col.types[i] == Tile.EMPTY) continue;
                float sx = originX + col.x * tileSize;
                float sy = originY + (col.bottomY + i) * tileSize;
                if (col.pieceTrigger) {
                    drawFallTriggerTile(sprites, sx, sy, tileSize, Tile.SINGLE_TILE);
                } else {
                    drawTileBackground(sprites, sx, sy, tileSize, col.types[i]);
                    drawTile(sprites, sx, sy, tileSize, col.types[i], Tile.SINGLE_TILE);
                }
            }
        }
    }

    private void drawActivePiecesWithBlocked(Board board, float originX, float originY, float tileSize,
                                             SpriteBatch sprites, float blockedWhiteAmt) {
        for (Piece piece : board.getActivePieces()) {
            if (piece.tiles == null || piece.location == null || piece.type == Tile.EMPTY) continue;
            boolean blocked = piece.isBlockedFromSpawning;
            for (int i = 0; i < piece.tiles.length; i++) {
                float sx = originX + (piece.location.x + piece.tiles[i].x) * tileSize;
                float sy = originY + (piece.location.y + piece.tiles[i].y) * tileSize;
                byte connection = piece.tileconnectionstates != null
                        ? piece.tileconnectionstates[i] : Tile.SINGLE_TILE;
                if (blocked) {
                    float amt = Math.max(0f, Math.min(1f, blockedWhiteAmt));
                    float r  = BLOCKED_GRAY.r + (BLOCKED_WHITE.r - BLOCKED_GRAY.r) * amt;
                    float g  = BLOCKED_GRAY.g + (BLOCKED_WHITE.g - BLOCKED_GRAY.g) * amt;
                    float bv = BLOCKED_GRAY.b + (BLOCKED_WHITE.b - BLOCKED_GRAY.b) * amt;
                    sprites.setColor(r * 0.5f, g * 0.5f, bv * 0.5f, 1f);
                    sprites.draw(tileBackground, sx, sy, tileSize, tileSize);
                    sprites.setColor(r, g, bv, 1f);
                    sprites.draw(tileRegions[connection & 0xF], sx, sy, tileSize, tileSize);
                } else if (piece.fallTrigger) {
                    drawFallTriggerTile(sprites, sx, sy, tileSize, connection);
                } else {
                    drawTileBackground(sprites, sx, sy, tileSize, piece.type);
                    drawTile(sprites, sx, sy, tileSize, piece.type, connection);
                }
            }
        }
    }

    /** White override for fall-trigger active minoes and piece-trigger falling minoes. */
    private void drawFallTriggerTile(SpriteBatch sprites, float sx, float sy, float tileSize,
                                     byte connectionstate) {
        sprites.setColor(FALL_TRIGGER_BG);
        sprites.draw(tileBackground, sx, sy, tileSize, tileSize);
        sprites.setColor(FALL_TRIGGER_WHITE);
        sprites.draw(tileRegions[connectionstate & 0xF], sx, sy, tileSize, tileSize);
    }

    private void drawTile(SpriteBatch sprites, float sx, float sy, float tileSize,
                          byte pieceType, byte connectionstate) {
        sprites.setColor(PieceTints.forType(pieceType));
        sprites.draw(tileRegions[connectionstate & 0xF], sx, sy, tileSize, tileSize);
    }

    private void drawTileBackground(SpriteBatch sprites, float sx, float sy, float tileSize,
                                    byte pieceType) {
        sprites.setColor(PieceTints.forTileBackground(pieceType));
        sprites.draw(tileBackground, sx, sy, tileSize, tileSize);
    }

    /**
     * Public helper for settings screens: draw a single tile foreground.
     * The caller is responsible for SpriteBatch begin/end.
     */
    public void drawTilePreview(SpriteBatch sprites, float x, float y, float size,
                                byte pieceType, byte connectionstate) {
        drawTile(sprites, x, y, size, pieceType, connectionstate);
    }

    /**
     * Public helper for settings screens: draw a single tile background.
     * The caller is responsible for SpriteBatch begin/end.
     */
    public void drawTileBgPreview(SpriteBatch sprites, float x, float y, float size,
                                  byte pieceType) {
        drawTileBackground(sprites, x, y, size, pieceType);
    }
}
