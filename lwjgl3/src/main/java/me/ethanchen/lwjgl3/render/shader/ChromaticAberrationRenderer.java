package me.ethanchen.lwjgl3.render.shader;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;

public class ChromaticAberrationRenderer implements ShaderRenderer {
    private ShaderProgram shader;
    private SpriteBatch batch;
    private FrameBuffer fbo;
    private TextureRegion fboRegion;
    private int fboWidth = -1;
    private int fboHeight = -1;
    private final Matrix4 proj = new Matrix4();
    public static int ACTIVE_FBO_HANDLE = 0;


    public ChromaticAberrationRenderer() {
        batch = new SpriteBatch();
        reloadShader();
    }

    @Override
    public void reloadShader() {
        ShaderProgram newShader = new ShaderProgram(
                Gdx.files.internal("shaders/chromatic_aberration.vert"),
                Gdx.files.internal("shaders/chromatic_aberration.frag")
        );
        if (!newShader.isCompiled()) {
            Gdx.app.error("ChromaticAberrationRenderer", "Shader compile error:\n" + newShader.getLog());
            newShader.dispose();
            return;
        }
        if (shader != null) {
            shader.dispose();
        }
        shader = newShader;
        Gdx.app.log("ChromaticAberrationRenderer", "Chromatic aberration shader successfully reloaded!");
    }

    private void ensureFboSize(int w, int h) {
        if (w <= 0 || h <= 0) return;
        if (w == fboWidth && h == fboHeight && fbo != null) return;
        if (fbo != null) fbo.dispose();
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        fboRegion = new TextureRegion(fbo.getColorBufferTexture());
        fboRegion.flip(false, true);
        fboWidth = w;
        fboHeight = h;
        proj.setToOrtho2D(0, 0, w, h);
        batch.setProjectionMatrix(proj);
    }

    /**
     * Begins capturing rendering into a full-screen framebuffer.
     * Anything drawn between begin() and end(direction, magnitude) will be captured and chromatically aberrated.
     */
    public void begin() {
        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();
        ensureFboSize(sw, sh);

        fbo.begin();
        ACTIVE_FBO_HANDLE = fbo.getFramebufferHandle();
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }


    /**
     * Ends framebuffer capture and renders the aberrated image to the screen.
     *
     * @param direction direction of aberration in radians
     * @param magnitude magnitude of aberration in normalized window coordinates (1.0 = 100% of window size)
     */
    public void end(float direction, float magnitude) {
        ACTIVE_FBO_HANDLE = 0;
        if (fbo == null) return;
        fbo.end();


        draw(fboRegion, 0, 0, fboWidth, fboHeight, direction, magnitude);
    }

    /**
     * Utility method to capture and render a Runnable action with chromatic aberration.
     *
     * @param renderAction action that draws the board or scene
     * @param direction    direction of aberration in radians
     * @param magnitude    magnitude of aberration in normalized window coordinates (1.0 = 100% of window size)
     */
    public void render(Runnable renderAction, float direction, float magnitude) {
        if (renderAction == null) return;
        begin();
        renderAction.run();
        end(direction, magnitude);
    }

    /**
     * Draws an existing TextureRegion with chromatic aberration applied.
     *
     * @param region    texture region to draw
     * @param x         screen X coordinate
     * @param y         screen Y coordinate
     * @param width     width to draw
     * @param height    height to draw
     * @param direction direction of aberration in radians
     * @param magnitude magnitude of aberration in normalized window coordinates (1.0 = 100% of window size)
     */
    public void draw(TextureRegion region, float x, float y, float width, float height,
                     float direction, float magnitude) {
        if (region == null || shader == null || !shader.isCompiled()) return;

        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();
        proj.setToOrtho2D(0, 0, sw, sh);
        batch.setProjectionMatrix(proj);
        batch.setShader(shader);

        shader.bind();
        if (shader.hasUniform("u_direction")) {
            shader.setUniformf("u_direction", direction);
        }
        if (shader.hasUniform("u_magnitude")) {
            shader.setUniformf("u_magnitude", magnitude);
        }

        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(region, x, y, width, height);
        batch.end();

        batch.setShader(null);
    }

    public void dispose() {
        if (shader != null) shader.dispose();
        if (batch != null) batch.dispose();
        if (fbo != null) fbo.dispose();
    }
}
