package me.ethanchen.lwjgl3.render.shader;

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
import me.ethanchen.lwjgl3.render.PieceTints;

/**
 * Full-screen glow pipeline: renders each active piece's mino tiles into an offscreen FBO,
 * then applies repeated separable Gaussian blurs in ping-pong FBOs, and finally composites
 * the result onto the screen with additive blending.
 */
public class GlowRenderer implements ShaderRenderer {

    public static float GLOW_BLUR_RADIUS = 6f;
    public static int GLOW_BLUR_PASSES = 8;

    private ShaderProgram blurShader;
    private final SpriteBatch glowSprites;
    private final ShapeRenderer glowShapes;
    private final Matrix4 glowProj = new Matrix4();
    private FrameBuffer glowFboA;
    private FrameBuffer glowFboB;
    private TextureRegion fboARegion;
    private TextureRegion fboBRegion;
    private int fboWidth = 0;
    private int fboHeight = 0;

    public GlowRenderer() {
        ShaderProgram.pedantic = false;
        loadShader();
        glowSprites = new SpriteBatch();
        glowShapes = new ShapeRenderer();
    }

    private void loadShader() {
        blurShader = new ShaderProgram(
                Gdx.files.internal("shaders/blur.vert"),
                Gdx.files.internal("shaders/blur.frag"));
        if (!blurShader.isCompiled()) {
            Gdx.app.error("GlowRenderer", "Blur shader compile error:\n" + blurShader.getLog());
        }
    }

    @Override
    public void reloadShader() {
        ShaderProgram newShader = new ShaderProgram(
                Gdx.files.internal("shaders/blur.vert"),
                Gdx.files.internal("shaders/blur.frag"));
        if (newShader.isCompiled()) {
            if (blurShader != null) blurShader.dispose();
            blurShader = newShader;
            Gdx.app.log("GlowRenderer", "Glow shader successfully reloaded!");
        } else {
            Gdx.app.error("GlowRenderer", "Failed to compile updated blur shader:\n" + newShader.getLog());
            newShader.dispose();
        }
    }

    public void draw(Board board, float originX, float originY, float tileSize, float[] glowStrengths) {
        draw(board, originX, originY, tileSize, glowStrengths, Gdx.graphics.getDeltaTime());
    }

    public void draw(Board board, float originX, float originY, float tileSize, float[] glowStrengths, float deltaTime) {
        if (!blurShader.isCompiled() || glowStrengths == null) return;

        boolean anyGlow = false;
        for (int i = 0; i < board.getActivePieces().size(); i++) {
            if (strengthAt(glowStrengths, i) > 0f) { anyGlow = true; break; }
        }
        if (!anyGlow) return;

        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();
        ensureFboSize(sw, sh);

        glowFboA.begin();
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        glowShapes.setProjectionMatrix(glowProj);
        glowShapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
        for (int i = 0; i < board.getActivePieces().size(); i++) {
            Piece piece = board.getActivePieces().get(i);
            float strength = strengthAt(glowStrengths, i);
            if (piece.tiles == null || piece.location == null || strength <= 0f) continue;
            float r, g, b;
            if (piece.fallTrigger) {
                // Fall-trigger pieces are drawn white; match the glow.
                r = g = b = strength > 1f ? 1f : strength;
            } else if (strength > 1f) {
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

        runBlurPasses(sw, sh, deltaTime);
    }

    public void dispose() {
        if (blurShader != null) blurShader.dispose();
        glowSprites.dispose();
        glowShapes.dispose();
        if (glowFboA != null) glowFboA.dispose();
        if (glowFboB != null) glowFboB.dispose();
    }

    private void runBlurPasses(int sw, int sh, float deltaTime) {
        glowSprites.setProjectionMatrix(glowProj);
        glowSprites.setShader(blurShader);
        glowSprites.setBlendFunction(GL20.GL_ONE, GL20.GL_ZERO);

        for (int pass = 0; pass < GLOW_BLUR_PASSES; pass++) {
            boolean lastPass = (pass == GLOW_BLUR_PASSES - 1);

            glowFboB.begin();
            Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            blurShader.bind();
            blurShader.setUniformf("u_blurDirection", GLOW_BLUR_RADIUS / sw, 0f);
            if (blurShader.hasUniform("u_deltaTime")) {
                blurShader.setUniformf("u_deltaTime", deltaTime);
            }
            glowSprites.begin();
            glowSprites.setColor(Color.WHITE);
            glowSprites.draw(fboARegion, 0, 0, sw, sh);
            glowSprites.end();
            glowFboB.end();

            if (lastPass) {
                glowSprites.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE);
            }
            blurShader.bind();
            blurShader.setUniformf("u_blurDirection", 0f, GLOW_BLUR_RADIUS / sh);
            if (blurShader.hasUniform("u_deltaTime")) {
                blurShader.setUniformf("u_deltaTime", deltaTime);
            }
            if (!lastPass) {
                glowFboA.begin();
                Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            } else if (ChromaticAberrationRenderer.ACTIVE_FBO_HANDLE != 0) {
                Gdx.gl20.glBindFramebuffer(GL20.GL_FRAMEBUFFER, ChromaticAberrationRenderer.ACTIVE_FBO_HANDLE);
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
        fboWidth = w;
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
