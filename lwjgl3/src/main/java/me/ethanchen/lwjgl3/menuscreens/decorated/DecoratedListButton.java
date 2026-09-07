package me.ethanchen.lwjgl3.menuscreens.decorated;

/**
 * List-row control: same chrome as {@link DecoratedButton} with a thinner frame.
 */
public class DecoratedListButton extends DecoratedButton {
    public static final float THIN_OUTLINE_DESIGN_PX = 2f;

    public DecoratedListButton(float designX, float designY, float designW, float designH,
                               String text, Runnable action) {
        super(designX, designY, designW, designH, text, action);
        this.outlineDesignPx = THIN_OUTLINE_DESIGN_PX;
        this.fontSize = 1.35f;
    }
}
