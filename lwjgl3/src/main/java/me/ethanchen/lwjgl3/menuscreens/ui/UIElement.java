package me.ethanchen.lwjgl3.menuscreens.ui;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;

import me.ethanchen.lwjgl3.menuscreens.MenuScreen;

public abstract class UIElement {
    public double centerX, centerY, width, height;

    public UIElement(double x, double y, double w, double h) {
        centerX = x;
        centerY = y;
        width = w;
        height = h;
    }

    public abstract void render(ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font);

    public abstract void onClick();

    public abstract void handleClick(int screenX, int screenY);

    public abstract void handleKeyTyped(char key);

    public void handleDrag(int screenX, int screenY) {}

    public void handleRelease(int screenX, int screenY) {}

    public boolean isClicked(int screenX, int screenY) {
        Vector2 v = MenuScreen.convertToRelCoords(screenX, screenY);
        float x = v.x;
        float y = v.y;
        System.out.println("x:" + x + "y:" + y); // debug
        if (x >= centerX-0.5*width && x <= centerX+0.5*width && y >= centerY-0.5*height && y <= centerY+0.5*height) {
            return true;
        }
        return false;
    }
}
