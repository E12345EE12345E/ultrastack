package me.ethanchen.lwjgl3.menuscreens;

import java.util.ArrayList;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;

import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.menuscreens.ui.AspectLockedViewport;
import me.ethanchen.lwjgl3.menuscreens.ui.UIElement;
import me.ethanchen.lwjgl3.menuscreens.ui.UITextBox;
import me.ethanchen.network.ClientPacketWrapper;

public abstract class MenuScreen extends InputAdapter {
    protected ArrayList<UIElement> elements; // menu buttons and text input boxes

    protected ClientApp app;
    protected ShapeRenderer shapes;
    protected SpriteBatch sprites;
    protected BitmapFont font;

    public MenuScreen(ClientApp app, ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font) {
        this.app = app;
        this.shapes = shapes;
        this.sprites = sprites;
        this.font = font;
        this.elements = new ArrayList<UIElement>();
        Gdx.input.setInputProcessor(this);
    }

    public abstract void update();

    /** Default render: iterates all elements. Override to add custom drawing before or after. */
    public void render() {
        elements.forEach(element -> element.render(shapes, sprites, font));
    }

    public void passClientPacket(ClientPacketWrapper w) {
        // empty method to be overwritten
    }

    /** Called when this screen is about to be replaced. Override to release resources such as controller listeners. */
    public void dispose() {}

    protected void onEscPressed() {
        // no-op by default; override in subclasses to handle escape
    }

    @Override
    public boolean keyDown(int keycode) {
        for (UIElement el : elements) {
            if (el instanceof UITextBox) {
                UITextBox box = (UITextBox) el;
                if (box.focused && box.handleKeyDown(keycode)) {
                    return true;
                }
            }
        }
        if (keycode == Input.Keys.ESCAPE) {
            onEscPressed();
            return true;
        }
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        for (UIElement el : elements) {
            el.handleClick(screenX, screenY);
        }
        return true;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        for (UIElement el : elements) {
            el.handleDrag(screenX, screenY);
        }
        return true;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        for (UIElement el : elements) {
            el.handleRelease(screenX, screenY);
        }
        return true;
    }

    @Override
    public boolean keyTyped(char character) {
        for (UIElement el : elements) {
            el.handleKeyTyped(character);
        }
        return true;
    }

    public static Vector2 convertToRelCoords(int screenX, int screenY) {
        return new Vector2(convertToRelCoordsX(screenX), convertToRelCoordsY(screenY));
    }

    public static Vector2 convertFromRelCoords(Vector2 v) {
        return new Vector2(convertFromRelCoordsX(v.x), convertFromRelCoordsY(v.y));
    }

    public static float convertToRelCoordsX(int screenX) {
        AspectLockedViewport vp = AspectLockedViewport.current();
        if (vp != null) return vp.toRelX(screenX);
        return (float) HdpiUtils.toBackBufferX(screenX) / Gdx.graphics.getWidth();
    }

    public static float convertToRelCoordsY(int screenY) {
        AspectLockedViewport vp = AspectLockedViewport.current();
        if (vp != null) return vp.toRelY(screenY);
        return (float) (Gdx.graphics.getHeight() - HdpiUtils.toBackBufferY(screenY)) / Gdx.graphics.getHeight();
    }

    /**
     * Screen X of a relative center (or left edge when used with half-width). For Simple UI this is
     * {@code rel * windowW}; for Aspect-locked UI it maps through the fitted design rect.
     */
    public static float convertFromRelCoordsX(float relX) {
        AspectLockedViewport vp = AspectLockedViewport.current();
        if (vp != null) return vp.toScreenX(relX);
        return relX * Gdx.graphics.getWidth();
    }

    /**
     * Top-down screen Y ({@code 0} at top of window) of a relative Y — kept for callers that
     * previously used this form. Prefer {@link #toScreenYBottom(float)} for new layout math.
     */
    public static float convertFromRelCoordsY(float relY) {
        AspectLockedViewport vp = AspectLockedViewport.current();
        if (vp != null) return Gdx.graphics.getHeight() - vp.toScreenY(relY);
        return Gdx.graphics.getHeight() - (relY * Gdx.graphics.getHeight());
    }

    /** Bottom-up screen Y of a relative Y ({@code 0} at bottom). */
    public static float toScreenYBottom(float relY) {
        AspectLockedViewport vp = AspectLockedViewport.current();
        if (vp != null) return vp.toScreenY(relY);
        return relY * Gdx.graphics.getHeight();
    }

    public static float toScreenWidth(float relW) {
        AspectLockedViewport vp = AspectLockedViewport.current();
        if (vp != null) return vp.toScreenW(relW);
        return relW * Gdx.graphics.getWidth();
    }

    public static float toScreenHeight(float relH) {
        AspectLockedViewport vp = AspectLockedViewport.current();
        if (vp != null) return vp.toScreenH(relH);
        return relH * Gdx.graphics.getHeight();
    }

    public static void linkTextBoxTabChain(ArrayList<UIElement> elements) {
        ArrayList<UITextBox> textBoxes = new ArrayList<UITextBox>();
        elements.forEach(element -> { if (element instanceof UITextBox) textBoxes.add((UITextBox) element); });
        if (textBoxes.size() > 1) {
            for (int i=0; i<textBoxes.size()-1; i++) {
                textBoxes.get(i).setNext(textBoxes.get(i+1));
            }
            textBoxes.get(textBoxes.size()-1).setNext(textBoxes.get(0));
        }
    }
}
