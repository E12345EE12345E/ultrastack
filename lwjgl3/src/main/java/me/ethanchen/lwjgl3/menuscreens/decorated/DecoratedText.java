package me.ethanchen.lwjgl3.menuscreens.decorated;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;

import me.ethanchen.lwjgl3.menuscreens.ui.DesignUi;
import me.ethanchen.lwjgl3.menuscreens.ui.UIFont;
import me.ethanchen.lwjgl3.menuscreens.ui.UIText;

/**
 * Decorated label. Non-focusable; uses the same {@link UIFont} scale convention as legacy text.
 */
public class DecoratedText extends DecoratedElement {
    public String text;
    public float fontSize;
    public UIText.TextAlign align = UIText.TextAlign.CENTER;
    public Color color = Color.WHITE;

    public DecoratedText(float designX, float designY, String text, float fontSize) {
        super(DesignUi.nx(designX), DesignUi.ny(designY), 0, 0);
        this.text = text;
        this.fontSize = fontSize;
        this.focusable = false;
    }

    @Override
    public boolean isFocusable() {
        return false;
    }

    @Override
    public void renderDecorated(DecorContext ctx) {
        if (!visible || alpha <= 0.01f) return;
        String draw = text != null ? text : "";
        float[] saved = UIFont.saveAndSetScale(ctx.font, fontSize);
        GlyphLayout layout = new GlyphLayout(ctx.font, draw);
        float pxX = pxCenterX();
        float pxY = pxCenterY();
        float x;
        float y;
        switch (align) {
            case TOP_LEFT:
                x = pxX;
                y = pxY;
                break;
            case TOP_CENTER:
                x = pxX - layout.width * 0.5f;
                y = pxY;
                break;
            case TOP_RIGHT:
                x = pxX - layout.width;
                y = pxY;
                break;
            case CENTER_LEFT:
                x = pxX;
                y = pxY + layout.height * 0.5f;
                break;
            case CENTER_RIGHT:
                x = pxX - layout.width;
                y = pxY + layout.height * 0.5f;
                break;
            case BOTTOM_LEFT:
                x = pxX;
                y = pxY + layout.height;
                break;
            case BOTTOM_CENTER:
                x = pxX - layout.width * 0.5f;
                y = pxY + layout.height;
                break;
            case BOTTOM_RIGHT:
                x = pxX - layout.width;
                y = pxY + layout.height;
                break;
            case CENTER:
            default:
                x = pxX - layout.width * 0.5f;
                y = pxY + layout.height * 0.5f;
                break;
        }
        float a = Anim.clamp01(alpha) * color.a;
        ctx.sprites.begin();
        ctx.font.setColor(color.r, color.g, color.b, a);
        ctx.font.draw(ctx.sprites, draw, x, y);
        ctx.sprites.end();
        ctx.font.setColor(Color.WHITE);
        UIFont.restoreScale(ctx.font, saved);
    }

    private float pxCenterX() {
        return me.ethanchen.lwjgl3.menuscreens.MenuScreen.convertFromRelCoordsX(relCenterX());
    }

    private float pxCenterY() {
        return me.ethanchen.lwjgl3.menuscreens.MenuScreen.toScreenYBottom(relCenterY());
    }
}
