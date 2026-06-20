package me.ethanchen.lwjgl3.menuscreens.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import me.ethanchen.lwjgl3.menuscreens.MenuScreen;

public class UIText extends UIElement {
    public enum TextAlign {
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        CENTER_LEFT,
        CENTER,
        CENTER_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_CENTER,
        BOTTOM_RIGHT
    }

    public TextInput textin;
    public float size;
    public TextAlign align = TextAlign.CENTER;

    public UIText(double x, double y, String text) {
        this(x, y, text, 1.0f);
    }

    public UIText(double x, double y, String text, TextAlign align) {
        this(x, y, text, 1.0f, align);
    }

    public UIText(double x, double y, String text, double size) { // for convenience for hardcoded numbers
        this(x, y, text, (float) size);
    }

    public UIText(double x, double y, String text, double size, TextAlign align) {
        this(x, y, text, (float) size, align);
    }

    public UIText(double x, double y, TextInput textInput, double size) {
        this(x, y, textInput, (float) size);
    }

    public UIText(double x, double y, TextInput textInput, double size, TextAlign align) {
        this(x, y, textInput, (float) size, align);
    }

    public UIText(double x, double y, String text, float size) {
        this(x, y, text, size, TextAlign.CENTER);
    }

    public UIText(double x, double y, String text, float size, TextAlign align) {
        super(x, y, 0, 0);
        this.textin = new TextInput(text);
        this.size = size;
        this.align = align;
    }

    public UIText(double x, double y, TextInput textInput, float size) {
        this(x, y, textInput, size, TextAlign.CENTER);
    }

    public UIText(double x, double y, TextInput textInput, float size, TextAlign align) {
        super(x, y, 0, 0);
        this.textin = textInput;
        this.size = size;
        this.align = align;
    }

    @Override
    public void render(ShapeRenderer shapes, SpriteBatch sprites, BitmapFont font) {
        float screenW = Gdx.graphics.getWidth();
        float screenH = Gdx.graphics.getHeight();

        float pxX = MenuScreen.convertFromRelCoordsX((float) centerX);
        float pxY = Gdx.graphics.getHeight() - MenuScreen.convertFromRelCoordsY((float) centerY);

        // Store original scale
        float originalScaleX = font.getScaleX();
        float originalScaleY = font.getScaleY();
        
        font.getData().setScale(1f);
        float unscaledLineHeight = font.getData().lineHeight;
        float scaleAdjustment = 15f / unscaledLineHeight;
        
        float fontScale = size * scaleAdjustment * (Gdx.graphics.getHeight() / 640f);
        font.getData().setScale(fontScale);

        sprites.begin();
        String text = textin.get();
        GlyphLayout layout = new GlyphLayout(font, (text != null) ? text : "");
        this.width = layout.width / screenW;
        this.height = layout.height / screenH;

        float x;
        float y;
        switch (align) {
            case TOP_LEFT:
                x = pxX;
                y = pxY;
                break;
            case TOP_CENTER:
                x = pxX - layout.width / 2f;
                y = pxY;
                break;
            case TOP_RIGHT:
                x = pxX - layout.width;
                y = pxY;
                break;
            case CENTER_LEFT:
                x = pxX;
                y = pxY + layout.height / 2f;
                break;
            case CENTER_RIGHT:
                x = pxX - layout.width;
                y = pxY + layout.height / 2f;
                break;
            case BOTTOM_LEFT:
                x = pxX;
                y = pxY + layout.height;
                break;
            case BOTTOM_CENTER:
                x = pxX - layout.width / 2f;
                y = pxY + layout.height;
                break;
            case BOTTOM_RIGHT:
                x = pxX - layout.width;
                y = pxY + layout.height;
                break;
            case CENTER:
            default:
                x = pxX - layout.width / 2f;
                y = pxY + layout.height / 2f;
                break;
        }
        font.setColor(Color.WHITE);
        font.draw(sprites, (text != null) ? text : "", x, y);
        sprites.end();

        // Restore original scale
        font.getData().setScale(originalScaleX, originalScaleY);
    }

    @Override
    public void onClick() {
    }

    @Override
    public void handleClick(int screenX, int screenY) {}

    @Override
    public void handleKeyTyped(char c) {}
}
