package me.ethanchen.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import org.lwjgl.glfw.GLFW;

/**
 * Aligns the initial desktop window with the monitor's DPI / content scale.
 *
 * <p>libGDX's LWJGL3 backend does not set {@code GLFW_SCALE_TO_MONITOR}, so
 * {@code setWindowedMode(640, 640)} is 640 physical pixels on Windows regardless of
 * 125%/150%/200% display scaling. The first {@code create}/{@code resize} also runs
 * before {@code glfwPollEvents}, so GLFW's content scale can still be 1.0 until a later
 * frame — or until the user resizes, which is what made this look like a resize-only fix.
 */
final class WindowDpi {

    static final int DESIGN_WIDTH = 640;
    static final int DESIGN_HEIGHT = 640;
    static final int MIN_WIDTH = 400;
    static final int MIN_HEIGHT = 400;

    private static final int POST_EVENT_SYNCS = 3;
    private static final float SCALE_EPSILON = 0.01f;

    private WindowDpi() {}

    static void applyTo(Lwjgl3ApplicationConfiguration configuration) {
        // getDisplayMode() initializes GLFW so monitor scale and platform are valid.
        Lwjgl3ApplicationConfiguration.getDisplayMode();
        float[] scale = primaryMonitorContentScale();
        int width = DESIGN_WIDTH;
        int height = DESIGN_HEIGHT;
        if (shouldScaleWindowSizeToContentScale()) {
            width = Math.max(MIN_WIDTH, Math.round(DESIGN_WIDTH * scale[0]));
            height = Math.max(MIN_HEIGHT, Math.round(DESIGN_HEIGHT * scale[1]));
        }
        configuration.setWindowedMode(width, height);
        configuration.setWindowSizeLimits(MIN_WIDTH, MIN_HEIGHT, -1, -1);
    }

    /**
     * Runs after GLFW has polled startup DPI events (libGDX processes runnables
     * after {@code glfwPollEvents}). Repeats a few times because content scale can
     * still be 1.0 on the first callback.
     */
    static void scheduleInitialSync(Runnable afterSync) {
        scheduleInitialSync(afterSync, POST_EVENT_SYNCS);
    }

    private static void scheduleInitialSync(Runnable afterSync, int remaining) {
        if (remaining <= 0) return;
        Gdx.app.postRunnable(() -> {
            syncAfterEvents();
            afterSync.run();
            scheduleInitialSync(afterSync, remaining - 1);
        });
    }

    static void syncAfterEvents() {
        if (!(Gdx.graphics instanceof Lwjgl3Graphics)) return;
        Lwjgl3Graphics graphics = (Lwjgl3Graphics) Gdx.graphics;
        long handle = graphics.getWindow().getWindowHandle();

        int[] winW = new int[1];
        int[] winH = new int[1];
        GLFW.glfwGetWindowSize(handle, winW, winH);

        float[] scaleX = new float[1];
        float[] scaleY = new float[1];
        GLFW.glfwGetWindowContentScale(handle, scaleX, scaleY);
        if (scaleX[0] < 0.5f) scaleX[0] = 1f;
        if (scaleY[0] < 0.5f) scaleY[0] = 1f;

        int targetW = winW[0];
        int targetH = winH[0];
        if (shouldScaleWindowSizeToContentScale()
                && (scaleX[0] > 1f + SCALE_EPSILON || scaleY[0] > 1f + SCALE_EPSILON)) {
            targetW = Math.max(MIN_WIDTH, Math.round(DESIGN_WIDTH * scaleX[0]));
            targetH = Math.max(MIN_HEIGHT, Math.round(DESIGN_HEIGHT * scaleY[0]));
        }

        if (targetW != graphics.getLogicalWidth() || targetH != graphics.getLogicalHeight()
                || targetW != winW[0] || targetH != winH[0]) {
            Gdx.graphics.setWindowedMode(targetW, targetH);
        }
    }

    private static float[] primaryMonitorContentScale() {
        float[] scaleX = new float[1];
        float[] scaleY = new float[1];
        scaleX[0] = 1f;
        scaleY[0] = 1f;
        long monitor = GLFW.glfwGetPrimaryMonitor();
        if (monitor != 0) {
            GLFW.glfwGetMonitorContentScale(monitor, scaleX, scaleY);
        }
        if (scaleX[0] < 0.5f) scaleX[0] = 1f;
        if (scaleY[0] < 0.5f) scaleY[0] = 1f;
        return new float[] {scaleX[0], scaleY[0]};
    }

    /**
     * Windows and X11 report window size in pixels (1:1 with the framebuffer). The
     * window must be multiplied by content scale to occupy the same physical size as
     * a 640² window at 100% DPI. macOS and Wayland already use point-sized windows.
     */
    private static boolean shouldScaleWindowSizeToContentScale() {
        int platform = GLFW.glfwGetPlatform();
        return platform == GLFW.GLFW_PLATFORM_WIN32 || platform == GLFW.GLFW_PLATFORM_X11;
    }
}
