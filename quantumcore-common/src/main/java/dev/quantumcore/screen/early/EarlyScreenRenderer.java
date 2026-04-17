package dev.quantumcore.screen.early;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_LINE_STRIP;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_PROJECTION;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glLineWidth;
import static org.lwjgl.opengl.GL11.glLoadIdentity;
import static org.lwjgl.opengl.GL11.glMatrixMode;
import static org.lwjgl.opengl.GL11.glOrtho;
import static org.lwjgl.opengl.GL11.glVertex2f;
import static org.lwjgl.opengl.GL11.glViewport;

public final class EarlyScreenRenderer {
    private static final float BG_R = 0.04f;
    private static final float BG_G = 0.04f;
    private static final float BG_B = 0.09f;
    private static final int BAR_HEIGHT = 4;
    private static final float SPINNER_SPEED_DEG = 180.0f;

    private static final float EMPTY_R = 0x1a / 255.0f;
    private static final float EMPTY_G = 0x3a / 255.0f;
    private static final float EMPTY_B = 0x5c / 255.0f;
    private static final float FILL_R = 0x44 / 255.0f;
    private static final float FILL_G = 0x88 / 255.0f;
    private static final float FILL_B = 0xff / 255.0f;

    private long windowHandle;
    private boolean initialized;
    private StbFontRenderer fontRenderer;
    private double lastFrameSeconds;
    private float spinnerAngleDeg;

    public void init(long windowHandle) {
        if (initialized) {
            return;
        }
        this.windowHandle = windowHandle;
        this.fontRenderer = new StbFontRenderer();
        this.initialized = true;
        this.lastFrameSeconds = GLFW.glfwGetTime();
        this.spinnerAngleDeg = 0.0f;
    }

    public void render(float progress, String currentStage, long startTimeNanos) {
        if (!initialized || windowHandle == 0L) {
            return;
        }

        float clampedProgress = Math.max(0.0f, Math.min(1.0f, progress));
        double now = GLFW.glfwGetTime();
        double delta = Math.max(0.0, now - lastFrameSeconds);
        lastFrameSeconds = now;
        spinnerAngleDeg = (spinnerAngleDeg + (float) (delta * SPINNER_SPEED_DEG)) % 360.0f;

        int width;
        int height;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            GLFW.glfwGetFramebufferSize(windowHandle, w, h);
            width = Math.max(1, w.get(0));
            height = Math.max(1, h.get(0));
        }

        glViewport(0, 0, width, height);
        glClearColor(BG_R, BG_G, BG_B, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0.0, width, height, 0.0, -1.0, 1.0);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        String stage = currentStage == null || currentStage.isBlank() ? "Initializing..." : currentStage;
        fontRenderer.renderText("QUANTUMCORE", 18.0f, 20.0f, 26.0f, 0.92f, 0.95f, 1.00f);
        fontRenderer.renderText(stage, (width * 0.5f) - (stage.length() * 5.5f), (height * 0.5f) - 36.0f, 24.0f, 0.89f, 0.92f, 0.98f);

        drawSpinner(width * 0.5f, height * 0.5f + 6.0f);
        drawProgressBar(width, height, clampedProgress, now);

        double elapsedSeconds = Math.max(0.0, (System.nanoTime() - startTimeNanos) / 1_000_000_000.0);
        String elapsed = String.format("%.1fs", elapsedSeconds);
        fontRenderer.renderText(elapsed, width - 64.0f, height - 18.0f, 16.0f, 0.82f, 0.85f, 0.92f);
        fontRenderer.renderText("Powered by QuantumCore", 10.0f, height - 18.0f, 14.0f, 0.50f, 0.56f, 0.64f);

        glDisable(GL_BLEND);
    }

    public void destroy() {
        if (fontRenderer != null) {
            fontRenderer.destroy();
            fontRenderer = null;
        }
        initialized = false;
        windowHandle = 0L;
    }

    private void drawSpinner(float cx, float cy) {
        float radius = 18.0f;
        float arcDegrees = 260.0f;
        int segments = 48;
        glLineWidth(2.0f);
        glColor4f(0.40f, 0.73f, 1.00f, 0.95f);
        glBegin(GL_LINE_STRIP);
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float deg = spinnerAngleDeg + t * arcDegrees;
            float rad = (float) Math.toRadians(deg);
            float x = cx + (float) Math.cos(rad) * radius;
            float y = cy + (float) Math.sin(rad) * radius;
            glVertex2f(x, y);
        }
        glEnd();
    }

    private void drawProgressBar(int width, int height, float progress, double timeSeconds) {
        float y0 = height - BAR_HEIGHT;
        float filled = width * progress;

        glColor4f(EMPTY_R, EMPTY_G, EMPTY_B, 1.0f);
        GL11.glRectf(0.0f, y0, width, height);

        float r = lerp(EMPTY_R, FILL_R, progress);
        float g = lerp(EMPTY_G, FILL_G, progress);
        float b = lerp(EMPTY_B, FILL_B, progress);
        glColor4f(r, g, b, 1.0f);
        GL11.glRectf(0.0f, y0, filled, height);

        if (filled > 2.0f) {
            float shimmerWidth = 12.0f;
            float shimmerX = (float) ((timeSeconds * 120.0) % Math.max(1.0f, filled + shimmerWidth));
            float shimmerStart = Math.max(0.0f, shimmerX - shimmerWidth);
            float shimmerEnd = Math.min(filled, shimmerX);
            if (shimmerEnd > shimmerStart) {
                glColor4f(0.82f, 0.92f, 1.00f, 0.26f);
                GL11.glRectf(shimmerStart, y0, shimmerEnd, height);
            }
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
