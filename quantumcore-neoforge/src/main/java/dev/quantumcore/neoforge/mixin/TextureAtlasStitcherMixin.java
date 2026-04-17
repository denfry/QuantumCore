package dev.quantumcore.neoforge.mixin;

import dev.quantumcore.common.cache.AtlasManifest;
import dev.quantumcore.common.cache.GpuAtlasCache;
import dev.quantumcore.common.concurrent.NotThreadSafe;
import dev.quantumcore.common.util.HashingUtils;
import dev.quantumcore.neoforge.QuantumCoreNeoForge;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.util.Map;

@Mixin(targets = "net.minecraft.client.renderer.texture.TextureAtlas")
@NotThreadSafe
public abstract class TextureAtlasStitcherMixin {
    @Inject(method = "reload", at = @At("HEAD"))
    private void quantumcore$tryCacheReload(CallbackInfo ci) {
        long inputHash = HashingUtils.xxh3("atlas:blocks".getBytes());
        GpuAtlasCache cache = QuantumCoreNeoForge.runtime().atlasCache();
        boolean hit = cache.readAtlas("blocks", inputHash).isPresent();
        QuantumCoreNeoForge.runtime().profiler().setAtlasLoadedFromCache(hit);
    }

    @Inject(method = "upload", at = @At("TAIL"))
    private void quantumcore$saveCache(CallbackInfo ci) {
        try {
            int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (width <= 0 || height <= 0) {
                return;
            }
            ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
            long hash = HashingUtils.xxh3("atlas:blocks".getBytes());
            QuantumCoreNeoForge.runtime().atlasCache().writeAtlas(
                "blocks",
                buffer,
                width,
                height,
                Map.of("minecraft:missingno", new AtlasManifest.SpriteUv(0.0F, 0.0F, 1.0F, 1.0F)),
                hash
            );
        } catch (Throwable ignored) {
        }
    }
}
