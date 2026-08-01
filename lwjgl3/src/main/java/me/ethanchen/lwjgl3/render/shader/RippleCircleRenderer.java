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
 * Renderer for drawing a rippling white circle or ellipse in board-relative coordinates.
 * Allows custom average thickness and ripple intensity parameters.
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
        draw(originX, originY, tileSize, boardX, boardY, radius, 1.0f, 1.0f, thickness, rippleIntensity);
    }

    public void draw(float originX, float originY, float tileSize,
                     float boardX, float boardY, float radius,
                     float widthMult, float heightMult,
                     float thickness, float rippleIntensity) {
        elapsedTime += Gdx.graphics.getDeltaTime();
        draw(originX, originY, tileSize, boardX, boardY, radius, widthMult, heightMult, thickness, rippleIntensity, elapsedTime);
    }

    public void draw(float originX, float originY, float tileSize,
                     float boardX, float boardY, float radius,
                     float widthMult, float heightMult,
                     float thickness, float rippleIntensity, float time) {
        if (shader == null || !shader.isCompiled()) return;

        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();
        if (sw <= 0 || sh <= 0) return;

        proj.setToOrtho2D(0, 0, sw, sh);
        batch.setProjectionMatrix(proj);
        batch.setShader(shader);

        float centerPxX = originX + (boardX + 0.5f) * tileSize;
        float centerPxY = originY + (boardY + 0.5f) * tileSize;

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

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        batch.begin();
        batch.setColor(Color.WHITE);
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
