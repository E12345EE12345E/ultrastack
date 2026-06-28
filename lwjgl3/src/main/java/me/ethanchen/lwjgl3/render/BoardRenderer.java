package me.ethanchen.lwjgl3.render;

import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;

import me.ethanchen.game.board.Board;
import me.ethanchen.game.board.Piece;
import me.ethanchen.game.board.Tile;

/**
 * Renders a {@link Board}: locked tiles, active pieces, grid outline, and optional per-piece glow.
 * Call {@link #dispose()} when done.
 */
public class BoardRenderer {
    private static BoardRenderer instance;

    private static final int TILE_SHEET_TILE_SIZE = 16;

    /**
     * Blur sample radius in pixels. Increase for a wider/softer glow, decrease for a tighter one.
     * The 5-tap Gaussian kernel extends to ~3.23 × this value in each direction.
     */
    public static float GLOW_BLUR_RADIUS = 6f;

    /**
     * Number of full H+V blur iterations. More passes produce a smoother, rounder glow.
     * 3–5 is a good range; beyond ~6 the improvement becomes imperceptible.
     */
    public static int GLOW_BLUR_PASSES = 6;

    // Tile textures
    private final Texture tileSheet;
    private final Texture tileBackground;
    private final TextureRegion[] tileRegions;

    private static final Color SHADOW_GRAY = new Color(0.6f, 0.6f, 0.6f, 0.5f);

    // Glow pipeline resources
    private final ShaderProgram blurShader;
    private final SpriteBatch glowSprites;
    private final ShapeRenderer glowShapes;
    private final Matrix4 glowProj = new Matrix4();
    private FrameBuffer glowFboA;
    private FrameBuffer glowFboB;
    private TextureRegion fboARegion;
    private TextureRegion fboBRegion;
    private int fboWidth = 0;
    private int fboHeight = 0;

    public static BoardRenderer getInstance() {
        if (instance == null) {
            instance = new BoardRenderer();
        }
        return instance;
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

        ShaderProgram.pedantic = false;
        blurShader = new ShaderProgram(
            Gdx.files.internal("shaders/blur.vert"),
            Gdx.files.internal("shaders/blur.frag")
        );
        if (!blurShader.isCompiled()) {
            Gdx.app.error("BoardRenderer", "Blur shader compile error:\n" + blurShader.getLog());
        }

        glowSprites = new SpriteBatch();
        glowShapes = new ShapeRenderer();
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

    /** Gray color used for blocked pieces (unlit, {@link PieceTints#GRAYSCALE_VALUE} value). */
    private static final Color BLOCKED_GRAY = new Color(PieceTints.GRAYSCALE_VALUE, PieceTints.GRAYSCALE_VALUE, PieceTints.GRAYSCALE_VALUE, 1f);
    /** White color used as the fully-lit blocked piece color during the explode countdown. */
    private static final Color BLOCKED_WHITE = new Color(1f, 1f, 1f, 1f);

    /**
     * Draws all non-empty locked tiles and active pieces.
     * {@code glowStrengths} sets per-active-piece glow intensity in [0, 1]; {@code null} disables glow.
     * Indices beyond the array length are treated as 0.
     * Caller must NOT have an open SpriteBatch or ShapeRenderer begin/end around this call.
     */
    public void drawBoard(Board board, float originX, float originY, float tileSize,
                          SpriteBatch sprites, float[] glowStrengths) {
        drawBoard(board, originX, originY, tileSize, sprites, glowStrengths, null, 0f, true, -1, 0f);
    }

    /**
     * Like {@link #drawBoard(Board, float, float, float, SpriteBatch, float[])} but also draws
     * per-piece drop shadows between the locked tiles and the active pieces.
     * {@code shadows} may be {@code null} to skip shadow rendering.
     */
    public void drawBoard(Board board, float originX, float originY, float tileSize,
                          SpriteBatch sprites, float[] glowStrengths, Board.ShadowInfo[] shadows) {
        drawBoard(board, originX, originY, tileSize, sprites, glowStrengths, shadows, 0f, true, -1, 0f);
    }

    /**
     * Full drawBoard overload with blocked-piece tinting and active-piece suppression.
     *
     * @param blockedWhiteAmt  0 = full gray for blocked pieces; 1 = full white. Interpolated linearly.
     * @param drawActivePieces When false, active pieces and their glow are not drawn (used post-explosion).
     * @param localPlayerId    Index of the local player; {@code -1} disables other-player grayscale.
     * @param otherPlayerGrayscaleAmt 0 = full color for other players; 1 = fully grayscale.
     */
    public void drawBoard(Board board, float originX, float originY, float tileSize,
                          SpriteBatch sprites, float[] glowStrengths, Board.ShadowInfo[] shadows,
                          float blockedWhiteAmt, boolean drawActivePieces,
                          int localPlayerId, float otherPlayerGrayscaleAmt) {
        if (drawActivePieces) {
            drawGlow(board, originX, originY, tileSize, glowStrengths);
        }
        sprites.begin();
        drawLockedTiles(board, originX, originY, tileSize, sprites);
        if (drawActivePieces) {
            drawShadowPieces(board, shadows, originX, originY, tileSize, sprites,
                    localPlayerId, otherPlayerGrayscaleAmt);
            drawActivePiecesWithBlocked(board, originX, originY, tileSize, sprites, blockedWhiteAmt);
        }
        sprites.setColor(Color.WHITE);
        sprites.end();
    }

    /**
     * Draws the glow halo for active pieces.
     * {@code glowStrengths} sets per-active-piece glow intensity in [0, 1]; {@code null} disables glow.
     * Indices beyond the array length are treated as 0.
     */
    public void drawGlow(Board board, float originX, float originY, float tileSize,
                         float[] glowStrengths) {
        if (!blurShader.isCompiled()) return;
        if (glowStrengths == null) return;

        boolean anyGlow = false;
        for (int i = 0; i < board.getActivePieces().size(); i++) {
            if (glowAt(glowStrengths, i) > 0f) {
                anyGlow = true;
                break;
            }
        }
        if (!anyGlow) return;

        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();
        ensureFboSize(sw, sh);

        // Pass 1: render solid tinted rects for each glowing mino into glowFboA
        glowFboA.begin();
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        glowShapes.setProjectionMatrix(glowProj);
        glowShapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE); // additive so overlapping pieces accumulate
        for (int i = 0; i < board.getActivePieces().size(); i++) {
            Piece piece = board.getActivePieces().get(i);
            float strength = glowAt(glowStrengths, i);
            if (piece.tiles == null || piece.location == null || strength <= 0f) continue;
            float r, g, b;
            if (strength > 1f) {
                // Values in (1, 2]: blend toward pure white. At 1.0 -> tinted, at 2.0 -> full white.
                float whiteBlend = Math.min(strength - 1f, 1f);
                Color c = PieceTints.forGlow(piece.type);
                r = c.r + (1f - c.r) * whiteBlend;
                g = c.g + (1f - c.g) * whiteBlend;
                b = c.b + (1f - c.b) * whiteBlend;
            } else {
                Color c = PieceTints.forGlow(piece.type);
                r = c.r * strength;
                g = c.g * strength;
                b = c.b * strength;
            }
            glowShapes.setColor(r, g, b, 1f);
            for (Vector2 offset : piece.tiles) {
                float sx = originX + (piece.location.x + offset.x) * tileSize;
                float sy = originY + (piece.location.y + offset.y) * tileSize;
                glowShapes.rect(sx, sy, tileSize, tileSize);
            }
        }
        glowShapes.end();
        glowFboA.end();

        runGlowBlurPasses(sw, sh);
    }

    /**
     * Draws the grid outline for every allowed cell.
     * Caller must NOT have an open ShapeRenderer begin/end around this call.
     */
    public void drawBoardGrid(Board board, float originX, float originY, float tileSize,
                              ShapeRenderer shapes) {
        boolean[][] allowed = board.getAllowedTiles();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Color.WHITE.r * 0.5f, Color.WHITE.g * 0.5f, Color.WHITE.b * 0.5f, Color.WHITE.a * 0.5f);
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
     *
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
     * Draws a translucent overlay over all {@code allowedTiles=true} cells in the given
     * board column.  Used to highlight the repeat-column penalty zone in score mode.
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

    private static final String[] BONUS_PCTS    = {"125%", "120%", "150%", "200%"};
    private static final String[] BONUS_LABELS  = {" - B2B clear",
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
                // Stack lines top-to-bottom; compute total height first
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

    private static final Color HOLD_GRAYSCALE = new Color(0.45f, 0.45f, 0.45f, 1f);

    /**
     * Draws the shared hold box to the left of the board.
     * The box is a white outline rectangle with a "HOLD" label; if a piece is held it
     * is drawn centred inside in full colour (when available) or grayscale (when locked).
     * Caller must NOT have an open SpriteBatch or ShapeRenderer begin/end around this call.
     */
    public void drawHoldBox(byte heldType, boolean available,
                            float x, float y, float boxSize, float tileSize,
                            ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font) {
        // White outline box
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Color.WHITE);
        shapes.rect(x, y, boxSize, boxSize);
        shapes.end();

        // "HOLD" label centered near the top of the box
        GlyphLayout layout = new GlyphLayout();
        float savedX = font.getScaleX(), savedY = font.getScaleY();
        font.getData().setScale(1f);
        float lh = font.getData().lineHeight;
        float fs = 0.6f * (tileSize / lh);
        font.getData().setScale(fs);
        layout.setText(font, "HOLD");
        float labelX = x + (boxSize - layout.width) * 0.5f;
        float labelY = y + boxSize - layout.height * 0.3f;
        sprites.begin();
        font.setColor(Color.WHITE);
        font.draw(sprites, "HOLD", labelX, labelY);
        sprites.end();
        font.getData().setScale(savedX, savedY);

        // Draw held piece centred in the box (if any)
        if (heldType != 0) {
            Piece piece = Piece.defaultPiece(heldType);
            // Compute bounding box of the tiles (relative to piece origin, ignoring location offset)
            float minTX = Float.MAX_VALUE, maxTX = -Float.MAX_VALUE;
            float minTY = Float.MAX_VALUE, maxTY = -Float.MAX_VALUE;
            for (Vector2 t : piece.tiles) {
                minTX = Math.min(minTX, t.x);
                maxTX = Math.max(maxTX, t.x + 1f);
                minTY = Math.min(minTY, t.y);
                maxTY = Math.max(maxTY, t.y + 1f);
            }
            // Centre the piece in the lower 3/4 of the box (below the label)
            float centerX = x + boxSize * 0.5f;
            float centerY = y + boxSize * 0.35f;
            float offsetX = centerX - (minTX + (maxTX - minTX) * 0.5f) * tileSize;
            float offsetY = centerY - (minTY + (maxTY - minTY) * 0.5f) * tileSize;

            Color tint = available ? PieceTints.forType(heldType) : HOLD_GRAYSCALE;
            sprites.begin();
            for (int i = 0; i < piece.tiles.length; i++) {
                float sx = offsetX + piece.tiles[i].x * tileSize;
                float sy = offsetY + piece.tiles[i].y * tileSize;
                byte connection = (piece.tileconnectionstates != null && i < piece.tileconnectionstates.length)
                        ? piece.tileconnectionstates[i] : Tile.SINGLE_TILE;
                Color bg = available ? PieceTints.forTileBackground(heldType)
                                     : new Color(0.2f, 0.2f, 0.2f, 1f);
                sprites.setColor(bg);
                sprites.draw(tileBackground, sx, sy, tileSize, tileSize);
                sprites.setColor(tint);
                sprites.draw(tileRegions[connection & 0xF], sx, sy, tileSize, tileSize);
            }
            sprites.setColor(Color.WHITE);
            sprites.end();
        }
    }

    /**
     * Draws a combined countdown timer + score box at the given position, styled to match
     * the hold box. Shows a "TIME" label at the top, the remaining MM:SS in the middle, and
     * the current score near the bottom. The timer is capped at 4:00.
     */
    public void drawTimerBox(long endTargetMs, long score, float x, float y, float boxSize, float tileSize,
                             ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font) {
        // White outline box
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Color.WHITE);
        shapes.rect(x, y, boxSize, boxSize);
        shapes.end();

        GlyphLayout layout = new GlyphLayout();
        float savedX = font.getScaleX(), savedY = font.getScaleY();
        font.getData().setScale(1f);
        float lh = font.getData().lineHeight;

        // "TIME" label near the top of the box
        float labelFs = 0.6f * (tileSize / lh);
        font.getData().setScale(labelFs);
        layout.setText(font, "TIME");
        float labelX = x + (boxSize - layout.width) * 0.5f;
        float labelY = y + boxSize - layout.height * 0.15f;
        sprites.begin();
        font.setColor(Color.WHITE);
        font.draw(sprites, "TIME", labelX, labelY);
        sprites.end();

        // MM:SS remaining (capped at 4:00), centered in the upper-middle portion
        long remaining = Math.min(4L * 60 * 1000, Math.max(0, endTargetMs - System.currentTimeMillis()));
        long mins = remaining / 60000;
        long secs = (remaining % 60000) / 1000;
        String timeText = mins + ":" + String.format("%02d", secs);
        float timeFs = 0.8f * (tileSize / lh);
        font.getData().setScale(timeFs);
        layout.setText(font, timeText);
        float timeX = x + (boxSize - layout.width) * 0.5f;
        float timeY = y + boxSize * 0.62f + layout.height * 0.5f;
        sprites.begin();
        font.setColor(Color.WHITE);
        font.draw(sprites, timeText, timeX, timeY);
        sprites.end();

        // Score in the lower portion
        String scoreText = String.valueOf(score);
        float scoreFs = 0.65f * (tileSize / lh);
        font.getData().setScale(scoreFs);
        layout.setText(font, scoreText);
        float scoreX = x + (boxSize - layout.width) * 0.5f;
        float scoreY = y + boxSize * 0.28f + layout.height * 0.5f;
        sprites.begin();
        font.setColor(Color.WHITE);
        font.draw(sprites, scoreText, scoreX, scoreY);
        sprites.end();

        font.getData().setScale(savedX, savedY);
    }

    public void dispose() {
        tileSheet.dispose();
        tileBackground.dispose();
        blurShader.dispose();
        glowSprites.dispose();
        glowShapes.dispose();
        if (glowFboA != null) glowFboA.dispose();
        if (glowFboB != null) glowFboB.dispose();
        if (instance == this) {
            instance = null;
        }
    }

    // -------------------------------------------------------------------------
    // Glow pipeline (continued in drawGlow above; blur passes below)
    // -------------------------------------------------------------------------

    private static float glowAt(float[] glowStrengths, int pieceIndex) {
        if (glowStrengths == null || pieceIndex >= glowStrengths.length) return 0f;
        return glowStrengths[pieceIndex];
    }

    private void runGlowBlurPasses(int sw, int sh) {
        // The final V-blur pass writes to the screen additively instead of into a FBO.
        glowSprites.setProjectionMatrix(glowProj);
        glowSprites.setShader(blurShader);
        glowSprites.setBlendFunction(GL20.GL_ONE, GL20.GL_ZERO); // overwrite for all FBO passes

        for (int pass = 0; pass < GLOW_BLUR_PASSES; pass++) {
            boolean lastPass = (pass == GLOW_BLUR_PASSES - 1);

            // Horizontal blur: A → B
            glowFboB.begin();
            Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            blurShader.bind();
            blurShader.setUniformf("u_blurDirection", GLOW_BLUR_RADIUS / sw, 0f);
            glowSprites.begin();
            glowSprites.setColor(Color.WHITE);
            glowSprites.draw(fboARegion, 0, 0, sw, sh);
            glowSprites.end();
            glowFboB.end();

            if (lastPass) {
                // Final vertical blur: B → screen (additive = glow halo behind sharp tiles)
                glowSprites.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE);
            }
            blurShader.bind();
            blurShader.setUniformf("u_blurDirection", 0f, GLOW_BLUR_RADIUS / sh);
            if (!lastPass) {
                // Intermediate vertical blur: B → A (keep ping-ponging)
                glowFboA.begin();
                Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            }
            glowSprites.begin();
            glowSprites.setColor(Color.WHITE);
            glowSprites.draw(fboBRegion, 0, 0, sw, sh);
            glowSprites.end();
            if (!lastPass) {
                glowFboA.end();
            }
        }

        // Restore batch to normal state
        glowSprites.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        glowSprites.setShader(null);
    }

    /** Recreates FBOs and syncs projection matrix if screen dimensions changed. */
    private void ensureFboSize(int w, int h) {
        if (w <= 0 || h <= 0) return;
        if (w == fboWidth && h == fboHeight) return;
        if (glowFboA != null) glowFboA.dispose();
        if (glowFboB != null) glowFboB.dispose();
        glowFboA = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        glowFboB = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        // FBO textures are Y-flipped relative to screen coords in LibGDX
        fboARegion = new TextureRegion(glowFboA.getColorBufferTexture());
        fboARegion.flip(false, true);
        fboBRegion = new TextureRegion(glowFboB.getColorBufferTexture());
        fboBRegion.flip(false, true);
        fboWidth = w;
        fboHeight = h;
        glowProj.setToOrtho2D(0, 0, w, h);
        glowShapes.setProjectionMatrix(glowProj);
        glowSprites.setProjectionMatrix(glowProj);
    }

    // -------------------------------------------------------------------------
    // Tile rendering helpers
    // -------------------------------------------------------------------------

    /**
     * Draws a ghost/drop-shadow for each active piece at the position it would reach on a hard drop.
     * Uses the same tilesheet as the actual pieces, no background, no glow, 50% alpha.
     * When {@code shadow.wouldPlace == false} the shadow is drawn gray (piece is hovering over
     * another active piece and would not lock).
     * The shadow is skipped when it is at the same board position as the live piece.
     */
    /**
     * @param localPlayerId       Index of the local player; {@code -1} disables other-player grayscale.
     * @param otherPlayerGrayscaleAmt 0 = full color for other players' shadows; 1 = fully grayscale.
     */
    private void drawShadowPieces(Board board, Board.ShadowInfo[] shadows,
                                  float originX, float originY, float tileSize,
                                  SpriteBatch sprites,
                                  int localPlayerId, float otherPlayerGrayscaleAmt) {
        if (shadows == null) return;
        float grayscaleAmt = Math.max(0f, Math.min(1f, otherPlayerGrayscaleAmt));
        for (int i = 0; i < shadows.length && i < board.getActivePieces().size(); i++) {
            Board.ShadowInfo shadow = shadows[i];
            if (shadow == null) continue;
            Piece piece = board.getActivePieces().get(i);
            if (piece.tiles == null || piece.location == null || piece.type == Tile.EMPTY) continue;
            if (piece.isBlockedFromSpawning) continue; // no drop shadow for blocked pieces

            // Skip when the shadow is coincident with the piece itself
            if (Math.abs(shadow.locationX - piece.location.x) < 0.01f
                    && Math.abs(shadow.locationY - piece.location.y) < 0.01f) continue;

            Color baseColor;
            if (shadow.wouldPlace) {
                float colorAmt = (localPlayerId >= 0 && i != localPlayerId) ? 1f - grayscaleAmt : 1f;
                Color c = PieceTints.blendGrayscale(piece.type, colorAmt, false);
                baseColor = new Color(c.r, c.g, c.b, 0.75f);
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

    private void drawActivePieces(Board board, float originX, float originY, float tileSize,
                                  SpriteBatch sprites) {
        drawActivePiecesWithBlocked(board, originX, originY, tileSize, sprites, 0f);
    }

    /**
     * Draws active pieces; blocked pieces are tinted between gray (blockedWhiteAmt=0)
     * and white (blockedWhiteAmt=1), while normal pieces use their standard PieceTints colors.
     */
    private void drawActivePiecesWithBlocked(Board board, float originX, float originY, float tileSize,
                                             SpriteBatch sprites, float blockedWhiteAmt) {
        for (Piece piece : board.getActivePieces()) {
            if (piece.tiles == null || piece.location == null || piece.type == Tile.EMPTY) continue;
            boolean blocked = piece.isBlockedFromSpawning;
            for (int i = 0; i < piece.tiles.length; i++) {
                float sx = originX + (piece.location.x + piece.tiles[i].x) * tileSize;
                float sy = originY + (piece.location.y + piece.tiles[i].y) * tileSize;
                byte connection = piece.tileconnectionstates != null
                    ? piece.tileconnectionstates[i]
                    : Tile.SINGLE_TILE;
                if (blocked) {
                    float amt = Math.max(0f, Math.min(1f, blockedWhiteAmt));
                    float r = BLOCKED_GRAY.r + (BLOCKED_WHITE.r - BLOCKED_GRAY.r) * amt;
                    float g = BLOCKED_GRAY.g + (BLOCKED_WHITE.g - BLOCKED_GRAY.g) * amt;
                    float bv = BLOCKED_GRAY.b + (BLOCKED_WHITE.b - BLOCKED_GRAY.b) * amt;
                    // Background: dark gray -> white
                    sprites.setColor(r * 0.5f, g * 0.5f, bv * 0.5f, 1f);
                    sprites.draw(tileBackground, sx, sy, tileSize, tileSize);
                    sprites.setColor(r, g, bv, 1f);
                    sprites.draw(tileRegions[connection & 0xF], sx, sy, tileSize, tileSize);
                } else {
                    drawTileBackground(sprites, sx, sy, tileSize, piece.type);
                    drawTile(sprites, sx, sy, tileSize, piece.type, connection);
                }
            }
        }
    }

    /**
     * Draws one tilesheet cell.
     * {@code connectionstate} is a 4-bit index (right=1, left=2, down=4, up=8).
     */
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
