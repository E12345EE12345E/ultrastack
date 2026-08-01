package me.ethanchen.lwjgl3.render.shader;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;

/**
 * Renderer for drawing a rippling white or custom colored circle/ellipse in board-relative coordinates.
 * Supports customizable average thickness, ripple intensity, and optional {@link RippleShaderColor} configuration.
 */
public class RippleCircleRenderer implements ShaderRenderer {

    private ShaderProgram shader;
    private final SpriteBatch batch;
    private final Matrix4 proj = new Matrix4();
    private Texture blankTexture;
    private float elapsedTime = 0f;

    public RippleCircleRenderer() {
        ShaderProgram.pedantic = false;
        loadShader();
        batch = new SpriteBatch();

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        blankTexture = new Texture(pixmap);
        pixmap.dispose();
    }

    private void loadShader() {
        shader = new ShaderProgram(
                Gdx.files.internal("shaders/ripple_circle.vert"),
                Gdx.files.internal("shaders/ripple_circle.frag"));
        if (!shader.isCompiled()) {
            Gdx.app.error("RippleCircleRenderer", "Ripple circle shader compile error:\n" + shader.getLog());
        }
    }

    @Override
    public void reloadShader() {
        ShaderProgram newShader = new ShaderProgram(
                Gdx.files.internal("shaders/ripple_circle.vert"),
                Gdx.files.internal("shaders/ripple_circle.frag"));
        if (newShader.isCompiled()) {
            if (shader != null) shader.dispose();
            shader = newShader;
            Gdx.app.log("RippleCircleRenderer", "Ripple circle shader successfully reloaded!");
        } else {
            Gdx.app.error("RippleCircleRenderer", "Failed to compile updated ripple circle shader:\n" + newShader.getLog());
            newShader.dispose();
        }
    }

    public void draw(float originX, float originY, float tileSize,
                     float boardX, float boardY, float radius,
                     float thickness, float rippleIntensity) {
        draw(originX, originY, tileSize, boardX, boardY, radius, 1.0f, 1.0f, thickness, rippleIntensity, null);
    }

    public void draw(float originX, float originY, float tileSize,
                     float boardX, float boardY, float radius,
                     float thickness, float rippleIntensity,
                     RippleShaderColor colorData) {
        draw(originX, originY, tileSize, boardX, boardY, radius, 1.0f, 1.0f, thickness, rippleIntensity, colorData);
    }

    public void draw(float originX, float originY, float tileSize,
                     float boardX, float boardY, float radius,
                     float widthMult, float heightMult,
                     float thickness, float rippleIntensity) {
        draw(originX, originY, tileSize, boardX, boardY, radius, widthMult, heightMult, thickness, rippleIntensity, null);
    }

    public void draw(float originX, float originY, float tileSize,
                     float boardX, float boardY, float radius,
                     float widthMult, float heightMult,
                     float thickness, float rippleIntensity,
                     RippleShaderColor colorData) {
        elapsedTime += Gdx.graphics.getDeltaTime();
        draw(originX, originY, tileSize, boardX, boardY, radius, widthMult, heightMult, thickness, rippleIntensity, colorData, elapsedTime);
    }

    public void draw(float originX, float originY, float tileSize,
                     float boardX, float boardY, float radius,
                     float widthMult, float heightMult,
                     float thickness, float rippleIntensity,
                     RippleShaderColor colorData, float time) {
        draw(originX, originY, tileSize, boardX, boardY, radius, widthMult, heightMult, thickness, rippleIntensity, colorData, time, 1f);
    }

    /**
     * Core draw call with an explicit animation {@code time} (caller-controlled clock, does not
     * auto-advance) and a global {@code alpha} multiplier applied on top of the shader's own
     * opacity pulse and per-color alpha. Useful for callers drawing multiple independently
     * time-driven ripples (e.g. one per player) that also need to fade in/out.
     */
    public void draw(float originX, float originY, float tileSize,
                     float boardX, float boardY, float radius,
                     float widthMult, float heightMult,
                     float thickness, float rippleIntensity,
                     RippleShaderColor colorData, float time, float alpha) {
        if (shader == null || !shader.isCompiled()) return;

        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();
        if (sw <= 0 || sh <= 0) return;

        proj.setToOrtho2D(0, 0, sw, sh);
        batch.setProjectionMatrix(proj);
        batch.setShader(shader);

        float centerPxX = originX + (boardX + 0.5f) * tileSize;
        float centerPxY = originY + (boardY + 0.5f) * tileSize;

        RippleShaderColor activeColor = (colorData != null) ? colorData : RippleShaderColor.DEFAULT;
        Color[] colors = activeColor.getColors();
        int numColors = Math.min(colors.length, 16);

        float[] colorArray = new float[numColors * 4];
        for (int i = 0; i < numColors; i++) {
            Color c = colors[i];
            colorArray[i * 4]     = c.r;
            colorArray[i * 4 + 1] = c.g;
            colorArray[i * 4 + 2] = c.b;
            colorArray[i * 4 + 3] = c.a;
        }

        shader.bind();
        if (shader.hasUniform("u_resolution")) {
            shader.setUniformf("u_resolution", sw, sh);
        }
        if (shader.hasUniform("u_center")) {
            shader.setUniformf("u_center", centerPxX, centerPxY);
        }
        if (shader.hasUniform("u_tileSize")) {
            shader.setUniformf("u_tileSize", tileSize);
        }
        if (shader.hasUniform("u_radius")) {
            shader.setUniformf("u_radius", radius);
        }
        if (shader.hasUniform("u_widthMult")) {
            shader.setUniformf("u_widthMult", widthMult);
        }
        if (shader.hasUniform("u_heightMult")) {
            shader.setUniformf("u_heightMult", heightMult);
        }
        if (shader.hasUniform("u_thickness")) {
            shader.setUniformf("u_thickness", thickness);
        }
        if (shader.hasUniform("u_rippleIntensity")) {
            shader.setUniformf("u_rippleIntensity", rippleIntensity);
        }
        if (shader.hasUniform("u_time")) {
            shader.setUniformf("u_time", time);
        }
        if (shader.hasUniform("u_colorCount")) {
            shader.setUniformi("u_colorCount", numColors);
        }
        if (shader.hasUniform("u_colors[0]")) {
            shader.setUniform4fv("u_colors[0]", colorArray, 0, numColors * 4);
        }
        if (shader.hasUniform("u_minOpacity")) {
            shader.setUniformf("u_minOpacity", activeColor.getMinOpacity());
        }
        if (shader.hasUniform("u_maxOpacity")) {
            shader.setUniformf("u_maxOpacity", activeColor.getMaxOpacity());
        }
        if (shader.hasUniform("u_colorMode")) {
            shader.setUniformi("u_colorMode", activeColor.getColorMode().getId());
        }
        if (shader.hasUniform("u_colorShiftSpeed")) {
            shader.setUniformf("u_colorShiftSpeed", activeColor.getColorShiftSpeed());
        }

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        batch.begin();
        batch.setColor(1f, 1f, 1f, alpha);
        batch.draw(blankTexture, 0, 0, sw, sh);
        batch.end();

        batch.setShader(null);
    }

    public void dispose() {
        if (shader != null) shader.dispose();
        if (batch != null) batch.dispose();
        if (blankTexture != null) blankTexture.dispose();
    }
}
