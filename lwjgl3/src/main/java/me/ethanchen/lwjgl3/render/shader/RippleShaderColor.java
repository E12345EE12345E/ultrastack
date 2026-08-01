package me.ethanchen.lwjgl3.render.shader;

import com.badlogic.gdx.graphics.Color;

/**
 * Data object holding color configuration for {@link RippleCircleRenderer}.
 * Stores an array of colors, color distribution mode (ANGULAR, RADIAL, NOISE),
 * position rotation/shift speed over time, and minimum/maximum opacity bounds.
 */
public class RippleShaderColor {

    public enum ColorMode {
        /** Colors spaced around the ring perimeter at different angles. */
        ANGULAR(0),
        /** Colors spaced across the outline thickness from inner to outer edge. */
        RADIAL(1),
        /** Colors randomly distributed based on smooth Simplex noise. */
        NOISE(2);

        private final int id;

        ColorMode(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }
    }

    public static final RippleShaderColor DEFAULT = new RippleShaderColor(
            new Color[] { new Color(1f, 1f, 1f, 1f) }, 0.88f, 1.0f, ColorMode.ANGULAR, 0.0f);

    private final Color[] colors;
    private final float minOpacity;
    private final float maxOpacity;
    private final ColorMode colorMode;
    private final float colorShiftSpeed;

    public RippleShaderColor(Color[] colors, float minOpacity, float maxOpacity, ColorMode colorMode, float colorShiftSpeed) {
        if (colors == null || colors.length == 0) {
            this.colors = new Color[] { new Color(1f, 1f, 1f, 1f) };
        } else {
            this.colors = colors;
        }
        this.minOpacity = minOpacity;
        this.maxOpacity = maxOpacity;
        this.colorMode = (colorMode != null) ? colorMode : ColorMode.ANGULAR;
        this.colorShiftSpeed = colorShiftSpeed;
    }

    public RippleShaderColor(Color[] colors, ColorMode colorMode, float colorShiftSpeed) {
        this(colors, 0.88f, 1.0f, colorMode, colorShiftSpeed);
    }

    public RippleShaderColor(Color... colors) {
        this(colors, 0.88f, 1.0f, ColorMode.ANGULAR, 0.2f);
    }

    public RippleShaderColor(float minOpacity, float maxOpacity, Color... colors) {
        this(colors, minOpacity, maxOpacity, ColorMode.ANGULAR, 0.2f);
    }

    public Color[] getColors() {
        return colors;
    }

    public float getMinOpacity() {
        return minOpacity;
    }

    public float getMaxOpacity() {
        return maxOpacity;
    }

    public ColorMode getColorMode() {
        return colorMode;
    }

    public float getColorShiftSpeed() {
        return colorShiftSpeed;
    }
}
