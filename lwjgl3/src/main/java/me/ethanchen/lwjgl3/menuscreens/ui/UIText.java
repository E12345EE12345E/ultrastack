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
    /**
     * When {@code > 0}, wrap text to this design-pixel width (1920×1080 canvas). Zero means
     * single-line / explicit-{@code \n} layout only.
     */
    public float wrapDesignWidth;

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
        float pxX = MenuScreen.convertFromRelCoordsX((float) centerX);
        float pxY = MenuScreen.toScreenYBottom((float) centerY);

        float[] savedScale = UIFont.saveAndSetScale(font, size);

        sprites.begin();
        String text = textin.get();
        if (text == null) text = "";

        // libGDX color tags like [#FFD700]I[] — used by artifact effect lines.
        boolean useMarkup = text.indexOf("[#") >= 0;
        boolean prevMarkup = font.getData().markupEnabled;
        font.getData().markupEnabled = useMarkup;

        GlyphLayout layout = new GlyphLayout();
        if (wrapDesignWidth > 0f) {
            float targetW = MenuScreen.toScreenWidth((float) DesignUi.nw(wrapDesignWidth));
            layout.setText(font, text, Color.WHITE, targetW, com.badlogic.gdx.utils.Align.left, true);
        } else {
            layout.setText(font, text);
        }
        AspectLockedViewport vp = AspectLockedViewport.current();
        float refW = vp != null ? vp.viewW : Gdx.graphics.getWidth();
        float refH = vp != null ? vp.viewH : Gdx.graphics.getHeight();
        this.width = layout.width / refW;
        this.height = layout.height / refH;

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
        font.draw(sprites, layout, x, y);
        sprites.end();

        font.getData().markupEnabled = prevMarkup;
        UIFont.restoreScale(font, savedScale);
    }

    @Override
    public void onClick() {
    }

    @Override
    public void handleClick(int screenX, int screenY) {}

    @Override
    public void handleKeyTyped(char c) {}
}
