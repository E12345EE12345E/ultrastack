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

import me.ethanchen.game.board.Piece;
import me.ethanchen.lwjgl3.render.PieceTints;

/**
 * Movement blur shader renderer: renders a motion trail for a given piece moving from
 * (startX, startY) to (endX, endY) in board coordinates, and applies a directional movement blur shader.
 */
public class MovementBlurRenderer implements ShaderRenderer {

    private ShaderProgram movementBlurShader;
    private final SpriteBatch sprites;
    private final ShapeRenderer shapes;
    private final Matrix4 proj = new Matrix4();
    private FrameBuffer fboA;
    private TextureRegion fboRegion;
    private int fboWidth = 0;
    private int fboHeight = 0;

    public MovementBlurRenderer() {
        ShaderProgram.pedantic = false;
        loadShader();
        sprites = new SpriteBatch();
        shapes = new ShapeRenderer();
    }

    private void loadShader() {
        movementBlurShader = new ShaderProgram(
                Gdx.files.internal("shaders/movement_blur.vert"),
                Gdx.files.internal("shaders/movement_blur.frag"));
        if (!movementBlurShader.isCompiled()) {
            Gdx.app.error("MovementBlurRenderer", "Movement blur shader compile error:\n" + movementBlurShader.getLog());
        }
    }

    @Override
    public void reloadShader() {
        ShaderProgram newShader = new ShaderProgram(
                Gdx.files.internal("shaders/movement_blur.vert"),
                Gdx.files.internal("shaders/movement_blur.frag"));
        if (newShader.isCompiled()) {
            if (movementBlurShader != null) movementBlurShader.dispose();
            movementBlurShader = newShader;
            Gdx.app.log("MovementBlurRenderer", "Movement blur shader successfully reloaded!");
        } else {
            Gdx.app.error("MovementBlurRenderer", "Failed to compile updated movement blur shader:\n" + newShader.getLog());
            newShader.dispose();
        }
    }

    public void draw(float originX, float originY, float tileSize, Piece piece,
                     int startX, int startY, int endX, int endY, float strength) {
        if (!movementBlurShader.isCompiled() || piece == null || piece.tiles == null || strength <= 0f) return;

        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();
        ensureFboSize(sw, sh);

        fboA.begin();
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        shapes.setProjectionMatrix(proj);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);


        float dx = endX - startX;
        float dy = endY - startY;

        Color baseColor = PieceTints.forGlow(piece.type);
        Color cBright = new Color(baseColor.r, baseColor.g, baseColor.b, Math.min(strength, 1.0f));
        Color cFaint  = new Color(baseColor.r, baseColor.g, baseColor.b, 0.0f);

        if (startX == endX) {
            java.util.Set<Integer> cols = new java.util.HashSet<Integer>();
            for (Vector2 offset : piece.tiles) {
                cols.add((int) offset.x);
            }
            for (int col : cols) {
                float pxLeft = originX + (endX + col) * tileSize;
                float pxWidth = tileSize;
                if (startY >= endY) {
                    float pxBottom = originY + endY * tileSize;
                    float pxTop    = originY + (startY + 1) * tileSize;
                    shapes.rect(pxLeft, pxBottom, pxWidth, pxTop - pxBottom,
                                cBright, cBright, cFaint, cFaint);
                } else {
                    float pxBottom = originY + startY * tileSize;
                    float pxTop    = originY + (endY + 1) * tileSize;
                    shapes.rect(pxLeft, pxBottom, pxWidth, pxTop - pxBottom,
                                cFaint, cFaint, cBright, cBright);
                }
            }
        } else if (startY == endY) {
            java.util.Set<Integer> rows = new java.util.HashSet<Integer>();
            for (Vector2 offset : piece.tiles) {
                rows.add((int) offset.y);
            }
            for (int row : rows) {
                float pxBottom = originY + (endY + row) * tileSize;
                float pxHeight = tileSize;
                if (startX >= endX) {
                    float pxLeft  = originX + endX * tileSize;
                    float pxRight = originX + (startX + 1) * tileSize;
                    shapes.rect(pxLeft, pxBottom, pxRight - pxLeft, pxHeight,
                                cBright, cFaint, cFaint, cBright);
                } else {
                    float pxLeft  = originX + startX * tileSize;
                    float pxRight = originX + (endX + 1) * tileSize;
                    shapes.rect(pxLeft, pxBottom, pxRight - pxLeft, pxHeight,
                                cFaint, cBright, cBright, cFaint);
                }
            }
        } else {
            for (Vector2 offset : piece.tiles) {
                float sx = originX + (endX + offset.x) * tileSize;
                float sy = originY + (endY + offset.y) * tileSize;
                shapes.setColor(baseColor.r, baseColor.g, baseColor.b, Math.min(strength, 1.0f));
                shapes.rect(sx, sy, tileSize, tileSize);
            }
        }
        shapes.end();
        fboA.end();
        if (ChromaticAberrationRenderer.ACTIVE_FBO_HANDLE != 0) {
            Gdx.gl20.glBindFramebuffer(GL20.GL_FRAMEBUFFER, ChromaticAberrationRenderer.ACTIVE_FBO_HANDLE);
        }

        sprites.setProjectionMatrix(proj);
        sprites.setShader(movementBlurShader);
        sprites.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);


        movementBlurShader.bind();
        if (movementBlurShader.hasUniform("u_motionVector")) {
            movementBlurShader.setUniformf("u_motionVector", (dx * tileSize) / sw, (dy * tileSize) / sh);
        }
        if (movementBlurShader.hasUniform("u_strength")) {
            movementBlurShader.setUniformf("u_strength", strength);
        }


        sprites.begin();
        sprites.setColor(Color.WHITE);
        sprites.draw(fboRegion, 0, 0, sw, sh);
        sprites.end();

        sprites.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sprites.setShader(null);
    }

    public void dispose() {
        if (movementBlurShader != null) movementBlurShader.dispose();
        sprites.dispose();
        shapes.dispose();
        if (fboA != null) fboA.dispose();
    }

    private void ensureFboSize(int w, int h) {
        if (w <= 0 || h <= 0) return;
        if (w == fboWidth && h == fboHeight) return;
        if (fboA != null) fboA.dispose();
        fboA = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        fboRegion = new TextureRegion(fboA.getColorBufferTexture());
        fboRegion.flip(false, true);
        fboWidth = w;
        fboHeight = h;
        proj.setToOrtho2D(0, 0, w, h);
        shapes.setProjectionMatrix(proj);
        sprites.setProjectionMatrix(proj);
    }
}
