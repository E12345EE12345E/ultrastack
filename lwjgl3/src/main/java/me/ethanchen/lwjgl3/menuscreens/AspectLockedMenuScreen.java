package me.ethanchen.lwjgl3.menuscreens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.ui.AspectLockedViewport;
import me.ethanchen.lwjgl3.menuscreens.ui.DesignUi;
import me.ethanchen.lwjgl3.menuscreens.ui.UIElement;

/**
 * Menu screen whose elements are laid out in a fixed {@link DesignUi#DESIGN_W}×{@link DesignUi#DESIGN_H}
 * canvas, fitted to the window with aspect preserved (letterbox/pillarbox). Simple UI screens
 * should keep extending {@link MenuScreen} directly.
 */
public abstract class AspectLockedMenuScreen extends MenuScreen {
    protected final AspectLockedViewport viewport =
            new AspectLockedViewport(DesignUi.DESIGN_W, DesignUi.DESIGN_H);

    public AspectLockedMenuScreen(ClientApp app, ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font) {
        super(app, shapes, sprites, font);
    }

    @Override
    public void render() {
        viewport.update();
        AspectLockedViewport.push(viewport);
        try {
            drawLetterbox();
            drawDesignDecorations();
            for (UIElement element : elements) {
                element.render(shapes, sprites, font);
            }
        } finally {
            AspectLockedViewport.pop();
        }
    }

    /**
     * Optional design-space chrome (dividers, etc.) drawn after the letterbox and before
     * {@link #elements}. Coordinates should use {@link DesignUi} / the active viewport.
     */
    protected void drawDesignDecorations() {}

    private void drawLetterbox() {
        float windowW = Gdx.graphics.getWidth();
        float windowH = Gdx.graphics.getHeight();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Color.BLACK);
        if (viewport.originX > 0.5f) {
            shapes.rect(0, 0, viewport.originX, windowH);
            shapes.rect(viewport.originX + viewport.viewW, 0, windowW - viewport.originX - viewport.viewW, windowH);
        }
        if (viewport.originY > 0.5f) {
            shapes.rect(0, 0, windowW, viewport.originY);
            shapes.rect(0, viewport.originY + viewport.viewH, windowW, windowH - viewport.originY - viewport.viewH);
        }
        shapes.end();
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        return withViewport(() -> super.touchDown(screenX, screenY, pointer, button));
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return withViewport(() -> super.touchDragged(screenX, screenY, pointer));
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return withViewport(() -> super.touchUp(screenX, screenY, pointer, button));
    }

    private boolean withViewport(BooleanSupplier action) {
        viewport.update();
        AspectLockedViewport.push(viewport);
        try {
            return action.getAsBoolean();
        } finally {
            AspectLockedViewport.pop();
        }
    }

    @FunctionalInterface
    private interface BooleanSupplier {
        boolean getAsBoolean();
    }
}
