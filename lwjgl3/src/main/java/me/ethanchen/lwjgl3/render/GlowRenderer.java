package me.ethanchen.lwjgl3.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;

import me.ethanchen.game.board.Board;
import me.ethanchen.game.board.Piece;

/**
 * Full-screen glow pipeline extracted from {@link BoardRenderer}: renders each active piece's
 * mino tiles into an offscreen FBO, then applies repeated separable Gaussian blurs in ping-pong
 * FBOs, and finally composites the result onto the screen with additive blending.
 *
 * <p>Obtain an instance via {@link BoardRenderer#getInstance()} — {@link BoardRenderer} owns the
 * {@code GlowRenderer} lifecycle and exposes it only indirectly through
 * {@link BoardRenderer#drawGlow}.
 */
public class GlowRenderer {

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

    private final ShaderProgram blurShader;
    private final SpriteBatch   glowSprites;
    private final ShapeRenderer glowShapes;
    private final Matrix4       glowProj = new Matrix4();
    private FrameBuffer  glowFboA;
    private FrameBuffer  glowFboB;
    private TextureRegion fboARegion;
    private TextureRegion fboBRegion;
    private int fboWidth  = 0;
    private int fboHeight = 0;

    GlowRenderer() {
        ShaderProgram.pedantic = false;
        blurShader = new ShaderProgram(
                Gdx.files.internal("shaders/blur.vert"),
                Gdx.files.internal("shaders/blur.frag"));
        if (!blurShader.isCompiled()) {
            Gdx.app.error("GlowRenderer", "Blur shader compile error:\n" + blurShader.getLog());
        }
        glowSprites = new SpriteBatch();
        glowShapes  = new ShapeRenderer();
    }

    // -------------------------------------------------------------------------
    // Public draw entry point
    // -------------------------------------------------------------------------

    /**
     * Renders per-piece glow halos for all active pieces with a positive strength in
     * {@code glowStrengths}. Strength values are in [0, 2]: 0 = no glow, 1 = tinted, 2 = white.
     *
     * <p>Caller must NOT have an open SpriteBatch or ShapeRenderer begin/end.
     */
    void draw(Board board, float originX, float originY, float tileSize, float[] glowStrengths) {
        if (!blurShader.isCompiled() || glowStrengths == null) return;

        boolean anyGlow = false;
        for (int i = 0; i < board.getActivePieces().size(); i++) {
            if (strengthAt(glowStrengths, i) > 0f) { anyGlow = true; break; }
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
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE); // additive: overlapping pieces accumulate
        for (int i = 0; i < board.getActivePieces().size(); i++) {
            Piece piece = board.getActivePieces().get(i);
            float strength = strengthAt(glowStrengths, i);
            if (piece.tiles == null || piece.location == null || strength <= 0f) continue;
            float r, g, b;
            if (strength > 1f) {
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

        runBlurPasses(sw, sh);
    }

    void dispose() {
        blurShader.dispose();
        glowSprites.dispose();
        glowShapes.dispose();
        if (glowFboA != null) glowFboA.dispose();
        if (glowFboB != null) glowFboB.dispose();
    }

    // -------------------------------------------------------------------------
    // Internal blur pipeline
    // -------------------------------------------------------------------------

    private void runBlurPasses(int sw, int sh) {
        glowSprites.setProjectionMatrix(glowProj);
        glowSprites.setShader(blurShader);
        glowSprites.setBlendFunction(GL20.GL_ONE, GL20.GL_ZERO);

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
                glowSprites.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE); // additive → screen
            }
            blurShader.bind();
            blurShader.setUniformf("u_blurDirection", 0f, GLOW_BLUR_RADIUS / sh);
            if (!lastPass) {
                glowFboA.begin();
                Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            }
            glowSprites.begin();
            glowSprites.setColor(Color.WHITE);
            glowSprites.draw(fboBRegion, 0, 0, sw, sh);
            glowSprites.end();
            if (!lastPass) glowFboA.end();
        }

        glowSprites.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        glowSprites.setShader(null);
    }

    private void ensureFboSize(int w, int h) {
        if (w <= 0 || h <= 0) return;
        if (w == fboWidth && h == fboHeight) return;
        if (glowFboA != null) glowFboA.dispose();
        if (glowFboB != null) glowFboB.dispose();
        glowFboA = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        glowFboB = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        fboARegion = new TextureRegion(glowFboA.getColorBufferTexture());
        fboARegion.flip(false, true);
        fboBRegion = new TextureRegion(glowFboB.getColorBufferTexture());
        fboBRegion.flip(false, true);
        fboWidth  = w;
        fboHeight = h;
        glowProj.setToOrtho2D(0, 0, w, h);
        glowShapes.setProjectionMatrix(glowProj);
        glowSprites.setProjectionMatrix(glowProj);
    }

    static float strengthAt(float[] glowStrengths, int pieceIndex) {
        if (glowStrengths == null || pieceIndex >= glowStrengths.length) return 0f;
        return glowStrengths[pieceIndex];
    }
}
