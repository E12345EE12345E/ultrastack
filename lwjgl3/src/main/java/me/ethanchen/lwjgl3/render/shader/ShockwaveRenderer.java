package me.ethanchen.lwjgl3.render.shader;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;

/**
 * Full-screen shockwave via screen-space displacement mapping: the scene is captured to an FBO,
 * then a ring-shaped displacement field warps the UV lookup as the wave expands from a point.
 */
public class ShockwaveRenderer implements ShaderRenderer {
    /** Wave lifetime in seconds at {@link #SPEED_NORMAL}. */
    public static final float LIFE_S = 0.62f;
    /** White crest fades to fully transparent over this many seconds. */
    public static final float CREST_FADE_S = 0.2f;
    /** Peak UV displacement (aspect-corrected) used as the "normal" amplitude. */
    public static final float AMPLITUDE_NORMAL = 0.048f;
    public static final float AMPLITUDE_LOW = 0.020f;
    public static final float AMPLITUDE_VERY_LOW = 0.008f;
    /** Speed multiplier; {@code 1} finishes a wave in {@link #LIFE_S}. */
    public static final float SPEED_NORMAL = 1f;
    public static final float SPEED_FAST = 2.25f;
    public static final float SPEED_LOW = 0.4f;
    /** Ring half-width in aspect-corrected UV (1 = screen height). */
    public static final float THICKNESS = 0.13f;
    private static final int MAX_WAVES = 4;
    private static final float MIN_SPEED = 0.05f;

    private ShaderProgram shader;
    private final SpriteBatch batch;
    private final Matrix4 proj = new Matrix4();
    private FrameBuffer fbo;
    private TextureRegion fboRegion;
    private int fboWidth = -1;
    private int fboHeight = -1;
    private boolean capturing;

    private final float[] centerX = new float[MAX_WAVES];
    private final float[] centerY = new float[MAX_WAVES];
    private final float[] progress = new float[MAX_WAVES];
    private final float[] maxRadius = new float[MAX_WAVES];
    private final float[] age = new float[MAX_WAVES];
    private final float[] lifeS = new float[MAX_WAVES];
    private final float[] amplitude = new float[MAX_WAVES];
    private final float[] crest = new float[MAX_WAVES];
    private int waveCount;

    private final float[] centerUv = new float[MAX_WAVES * 2];

    public ShockwaveRenderer() {
        ShaderProgram.pedantic = false;
        batch = new SpriteBatch();
        reloadShader();
    }

    @Override
    public void reloadShader() {
        ShaderProgram newShader = new ShaderProgram(
                Gdx.files.internal("shaders/shockwave.vert"),
                Gdx.files.internal("shaders/shockwave.frag"));
        if (!newShader.isCompiled()) {
            Gdx.app.error("ShockwaveRenderer", "Shader compile error:\n" + newShader.getLog());
            newShader.dispose();
            return;
        }
        if (shader != null) shader.dispose();
        shader = newShader;
        Gdx.app.log("ShockwaveRenderer", "Shockwave shader successfully reloaded!");
    }

    public boolean hasActive() {
        return waveCount > 0;
    }

    /**
     * Starts a shockwave at {@code (screenX, screenY)} in pixels.
     *
     * @param amplitude peak UV displacement (see {@link #AMPLITUDE_NORMAL})
     * @param speed     expansion rate vs {@link #SPEED_NORMAL} ({@code 2} = twice as fast)
     */
    public void spawn(float screenX, float screenY, float amplitude, float speed) {
        int sw = Math.max(1, Gdx.graphics.getWidth());
        int sh = Math.max(1, Gdx.graphics.getHeight());
        float aspectX = sw / (float) sh;
        float uvx = screenX / sw;
        float uvy = screenY / sh;
        float maxR = 0f;
        maxR = Math.max(maxR, aspectLen(uvx, uvy, 0f, 0f, aspectX));
        maxR = Math.max(maxR, aspectLen(uvx, uvy, 1f, 0f, aspectX));
        maxR = Math.max(maxR, aspectLen(uvx, uvy, 0f, 1f, aspectX));
        maxR = Math.max(maxR, aspectLen(uvx, uvy, 1f, 1f, aspectX));
        maxR *= 1.08f;

        if (waveCount >= MAX_WAVES) {
            for (int i = 1; i < MAX_WAVES; i++) copyWave(i, i - 1);
            waveCount = MAX_WAVES - 1;
        }
        int i = waveCount++;
        centerX[i] = uvx;
        centerY[i] = uvy;
        progress[i] = 0f;
        maxRadius[i] = maxR;
        age[i] = 0f;
        lifeS[i] = LIFE_S / Math.max(MIN_SPEED, speed);
        this.amplitude[i] = amplitude;
        crest[i] = 1f;
    }

    public void update(float dtS) {
        int w = 0;
        for (int i = 0; i < waveCount; i++) {
            age[i] += dtS;
            if (age[i] >= lifeS[i]) continue;
            copyWave(i, w);
            float life = Math.max(1e-4f, lifeS[w]);
            progress[w] = Math.min(1f, age[w] / life);
            crest[w] = 1f - Math.min(1f, age[w] / CREST_FADE_S);
            w++;
        }
        waveCount = w;
    }

    /**
     * Begins capturing the scene into a full-screen framebuffer. Nested glow/blur passes
     * restore here via {@link ChromaticAberrationRenderer#ACTIVE_FBO_HANDLE}.
     */
    public void begin() {
        if (capturing) return;
        ensureFboSize(bufferW(), bufferH());
        if (fbo == null) return;

        fbo.begin();
        capturing = true;
        ChromaticAberrationRenderer.ACTIVE_FBO_HANDLE = fbo.getFramebufferHandle();
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }

    /** Ends capture and blits the displaced scene to the screen. */
    public void end() {
        if (!capturing) return;
        capturing = false;
        ChromaticAberrationRenderer.ACTIVE_FBO_HANDLE = 0;
        fbo.end();
        blit();
    }

    private void blit() {
        if (fboRegion == null || shader == null || !shader.isCompiled()) return;

        // Match the app's logical-camera + HDPI viewport so the copy is 1:1 with a normal frame.
        HdpiUtils.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();
        proj.setToOrtho2D(0, 0, sw, sh);
        batch.setProjectionMatrix(proj);
        batch.setShader(shader);

        for (int i = 0; i < waveCount; i++) {
            centerUv[i * 2] = centerX[i];
            centerUv[i * 2 + 1] = centerY[i];
        }

        shader.bind();
        if (shader.hasUniform("u_resolution")) {
            shader.setUniformf("u_resolution", (float) bufferW(), (float) bufferH());
        }
        if (shader.hasUniform("u_waveCount")) {
            shader.setUniformi("u_waveCount", waveCount);
        }
        if (waveCount > 0 && shader.hasUniform("u_centers[0]")) {
            shader.setUniform2fv("u_centers[0]", centerUv, 0, waveCount * 2);
        }
        if (waveCount > 0 && shader.hasUniform("u_progress[0]")) {
            shader.setUniform1fv("u_progress[0]", progress, 0, waveCount);
        }
        if (waveCount > 0 && shader.hasUniform("u_maxRadius[0]")) {
            shader.setUniform1fv("u_maxRadius[0]", maxRadius, 0, waveCount);
        }
        if (waveCount > 0 && shader.hasUniform("u_amplitudes[0]")) {
            shader.setUniform1fv("u_amplitudes[0]", amplitude, 0, waveCount);
        }
        if (waveCount > 0 && shader.hasUniform("u_crest[0]")) {
            shader.setUniform1fv("u_crest[0]", crest, 0, waveCount);
        }
        if (shader.hasUniform("u_thickness")) {
            shader.setUniformf("u_thickness", THICKNESS);
        }

        // The FBO already holds the composited scene. A second SRC_ALPHA blend would
        // restripe translucent grid lines (alpha 0.5) and pop when the effect ends.
        batch.disableBlending();
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(fboRegion, 0, 0, sw, sh);
        batch.end();
        batch.enableBlending();
        batch.setShader(null);
    }

    public void dispose() {
        if (shader != null) shader.dispose();
        if (batch != null) batch.dispose();
        if (fbo != null) fbo.dispose();
        shader = null;
        fbo = null;
        fboRegion = null;
    }

    private void copyWave(int from, int to) {
        if (from == to) return;
        centerX[to] = centerX[from];
        centerY[to] = centerY[from];
        progress[to] = progress[from];
        maxRadius[to] = maxRadius[from];
        age[to] = age[from];
        lifeS[to] = lifeS[from];
        amplitude[to] = amplitude[from];
        crest[to] = crest[from];
    }

    private void ensureFboSize(int w, int h) {
        if (w <= 0 || h <= 0) return;
        if (w == fboWidth && h == fboHeight && fbo != null) return;
        if (fbo != null) fbo.dispose();
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        Texture tex = fbo.getColorBufferTexture();
        tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        tex.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        fboRegion = new TextureRegion(tex);
        fboRegion.flip(false, true);
        fboWidth = w;
        fboHeight = h;
        proj.setToOrtho2D(0, 0, w, h);
        batch.setProjectionMatrix(proj);
    }

    private static int bufferW() {
        return Math.max(1, Gdx.graphics.getBackBufferWidth());
    }

    private static int bufferH() {
        return Math.max(1, Gdx.graphics.getBackBufferHeight());
    }

    private static float aspectLen(float x0, float y0, float x1, float y1, float aspectX) {
        float dx = (x1 - x0) * aspectX;
        float dy = y1 - y0;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
