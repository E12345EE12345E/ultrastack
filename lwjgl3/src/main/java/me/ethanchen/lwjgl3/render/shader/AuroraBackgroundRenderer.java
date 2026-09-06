package me.ethanchen.lwjgl3.render.shader;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;

/**
 * Galaxy-aurora backdrop: translucent pink/green curtains over black, plus twinkling
 * stars. The expensive procedural pass is rendered into a half-resolution FBO and
 * linearly upsampled to the window.
 */
public class AuroraBackgroundRenderer implements ShaderRenderer {
    private ShaderProgram shader;
    private final SpriteBatch batch;
    private final Matrix4 proj = new Matrix4();
    private Texture blankTexture;
    private FrameBuffer fbo;
    private TextureRegion fboRegion;
    private int fboWidth = -1;
    private int fboHeight = -1;

    public AuroraBackgroundRenderer() {
        ShaderProgram.pedantic = false;
        batch = new SpriteBatch();
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        blankTexture = new Texture(pixmap);
        pixmap.dispose();
        reloadShader();
    }

    @Override
    public void reloadShader() {
        ShaderProgram newShader = new ShaderProgram(
                Gdx.files.internal("shaders/aurora.vert"),
                Gdx.files.internal("shaders/aurora.frag"));
        if (!newShader.isCompiled()) {
            Gdx.app.error("AuroraBackgroundRenderer", "Shader compile error:\n" + newShader.getLog());
            newShader.dispose();
            return;
        }
        if (shader != null) shader.dispose();
        shader = newShader;
        Gdx.app.log("AuroraBackgroundRenderer", "Aurora shader successfully reloaded!");
    }

    /**
     * Shades a half-res quad, then blits it to the window. {@code timeS} is the
     * animation clock; {@code alpha} scales overall intensity.
     */
    public void draw(float timeS, float alpha) {
        if (shader == null || !shader.isCompiled()) return;
        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();
        if (sw <= 0 || sh <= 0) return;

        int fboW = Math.max(1, bufferW() / 2);
        int fboH = Math.max(1, bufferH() / 2);
        ensureFboSize(fboW, fboH);
        if (fbo == null || fboRegion == null) return;

        fbo.begin();
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Write the shader's rgba straight into the FBO. SpriteBatch.begin() re-enables
        // GL blend unless blending is disabled on the batch — blending onto a cleared
        // (0,0,0,0) target squares alpha, and the upsample then multiplies again,
        // which made the aurora vanish.
        proj.setToOrtho2D(0, 0, fboW, fboH);
        batch.setProjectionMatrix(proj);
        batch.setShader(shader);
        batch.disableBlending();
        batch.begin();
        if (shader.hasUniform("u_resolution")) {
            shader.setUniformf("u_resolution", (float) fboW, (float) fboH);
        }
        if (shader.hasUniform("u_time")) {
            shader.setUniformf("u_time", timeS);
        }
        if (shader.hasUniform("u_intensity")) {
            shader.setUniformf("u_intensity", Math.max(0f, alpha));
        }
        if (shader.hasUniform("u_starDensity")) {
            shader.setUniformf("u_starDensity", 1f);
        }
        batch.setColor(Color.WHITE);
        batch.draw(blankTexture, 0, 0, fboW, fboH);
        batch.end();
        batch.setShader(null);
        fbo.end();

        Gdx.gl.glViewport(0, 0, bufferW(), bufferH());
        proj.setToOrtho2D(0, 0, sw, sh);
        batch.setProjectionMatrix(proj);
        batch.enableBlending();
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(fboRegion, 0, 0, sw, sh);
        batch.end();
    }

    public void dispose() {
        if (shader != null) shader.dispose();
        if (batch != null) batch.dispose();
        if (blankTexture != null) blankTexture.dispose();
        if (fbo != null) fbo.dispose();
        shader = null;
        blankTexture = null;
        fbo = null;
        fboRegion = null;
    }

    private void ensureFboSize(int w, int h) {
        if (w <= 0 || h <= 0) return;
        if (w == fboWidth && h == fboHeight && fbo != null) return;
        if (fbo != null) fbo.dispose();
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        Texture tex = fbo.getColorBufferTexture();
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        tex.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        fboRegion = new TextureRegion(tex);
        fboRegion.flip(false, true);
        fboWidth = w;
        fboHeight = h;
    }

    private static int bufferW() {
        return Math.max(1, Gdx.graphics.getBackBufferWidth());
    }

    private static int bufferH() {
        return Math.max(1, Gdx.graphics.getBackBufferHeight());
    }
}
