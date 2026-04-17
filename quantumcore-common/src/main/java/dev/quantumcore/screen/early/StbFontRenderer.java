package dev.quantumcore.screen.early;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.GL_ALPHA;
import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_RED;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColor3f;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexCoord2f;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glVertex2f;

public final class StbFontRenderer {
    private static final int BITMAP_W = 512;
    private static final int BITMAP_H = 512;
    private static final int FIRST_CHAR = 32;
    private static final int CHAR_COUNT = 96;
    private static final float BASE_PIXEL_HEIGHT = 24.0f;

    private final STBTTBakedChar.Buffer chars;
    private final int textureId;

    public StbFontRenderer() {
        this.chars = STBTTBakedChar.malloc(CHAR_COUNT);
        ByteBuffer bitmap = BufferUtils.createByteBuffer(BITMAP_W * BITMAP_H);
        ByteBuffer ttfBuffer = loadFont("/assets/quantumcore/fonts/roboto.ttf");

        int baked = STBTruetype.stbtt_BakeFontBitmap(ttfBuffer, BASE_PIXEL_HEIGHT, bitmap, BITMAP_W, BITMAP_H, FIRST_CHAR, chars);
        if (baked <= 0) {
            chars.free();
            throw new IllegalStateException("Failed to bake Roboto font");
        }

        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, BITMAP_W, BITMAP_H, 0, GL_RED, GL_UNSIGNED_BYTE, bitmap);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void renderText(String text, float x, float y, float size, float r, float g, float b) {
        if (text == null || text.isEmpty()) {
            return;
        }

        float scale = size / BASE_PIXEL_HEIGHT;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            STBTTAlignedQuad quad = STBTTAlignedQuad.malloc(stack);
            FloatBuffer xBuf = stack.floats(x);
            FloatBuffer yBuf = stack.floats(y);

            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glEnable(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, textureId);
            glColor3f(r, g, b);
            glBegin(GL_QUADS);
            for (int i = 0; i < text.length(); i++) {
                int cp = text.charAt(i);
                if (cp < FIRST_CHAR || cp >= FIRST_CHAR + CHAR_COUNT) {
                    cp = '?';
                }

                STBTruetype.stbtt_GetBakedQuad(chars, BITMAP_W, BITMAP_H, cp - FIRST_CHAR, xBuf, yBuf, quad, true);
                float x0 = x + (quad.x0() - x) * scale;
                float y0 = y + (quad.y0() - y) * scale;
                float x1 = x + (quad.x1() - x) * scale;
                float y1 = y + (quad.y1() - y) * scale;

                glTexCoord2f(quad.s0(), quad.t0());
                glVertex2f(x0, y0);
                glTexCoord2f(quad.s1(), quad.t0());
                glVertex2f(x1, y0);
                glTexCoord2f(quad.s1(), quad.t1());
                glVertex2f(x1, y1);
                glTexCoord2f(quad.s0(), quad.t1());
                glVertex2f(x0, y1);
            }
            glEnd();
            glBindTexture(GL_TEXTURE_2D, 0);
            glDisable(GL_TEXTURE_2D);
        }
    }

    public void destroy() {
        chars.free();
        glDeleteTextures(textureId);
    }

    private static ByteBuffer loadFont(String path) {
        try (InputStream stream = StbFontRenderer.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing font resource: " + path);
            }
            byte[] bytes = stream.readAllBytes();
            ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes).flip();
            return buffer;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load font: " + path, e);
        }
    }
}
