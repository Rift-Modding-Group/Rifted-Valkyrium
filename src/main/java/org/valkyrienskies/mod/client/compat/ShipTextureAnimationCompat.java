package org.valkyrienskies.mod.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.BlockRenderLayer;
import net.minecraftforge.fml.common.Loader;

import javax.annotation.ParametersAreNonnullByDefault;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Compat for chibi and celeritas.
 * Keeps the animated textures in visible ship meshes active between mesh rebuilds.
 */
@ParametersAreNonnullByDefault
public class ShipTextureAnimationCompat {
    private final Map<BlockRenderLayer, Set<TextureAtlasSprite>> sprites = new EnumMap<>(BlockRenderLayer.class);

    public void collect(BlockRenderLayer layer, BufferBuilder buffer) {
        if (!Loader.isModLoaded("celeritas") && !Loader.isModLoaded("loliasm")) return;

        Set<TextureAtlasSprite> layerSprites = new HashSet<>();
        this.sprites.put(layer, layerSprites);
        Set<float[]> coordinates = new HashSet<>(); //uvs as a size 2 array
        ByteBuffer vertices = buffer.getByteBuffer();
        int stride = buffer.getVertexFormat().getSize();
        int uvOffset = buffer.getVertexFormat().getUvOffsetById(0);
        //sample quad centers so atlas borders and partially textured faces resolve reliably
        for (int quad = 0; quad + 3 < buffer.getVertexCount(); quad += 4) {
            float u = 0;
            float v = 0;
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = (quad + vertex) * stride + uvOffset;
                u += vertices.getFloat(offset) * 0.25F;
                v += vertices.getFloat(offset + Float.BYTES) * 0.25F;
            }
            coordinates.add(new float[]{u, v});
        }

        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        for (float[] uv : coordinates) {
            TextureAtlasSprite sprite = null;
            if (Loader.isModLoaded("celeritas")) sprite = CeleritasTextureCompat.findSprite(atlas, uv[0], uv[1]);
            if (sprite == null && Loader.isModLoaded("loliasm")) sprite = ChibiTextureCompat.findSprite(atlas, uv[0], uv[1]);
            if (sprite != null && sprite.hasAnimationMetadata()
                    && uv[0] >= sprite.getMinU() && uv[0] <= sprite.getMaxU()
                    && uv[1] >= sprite.getMinV() && uv[1] <= sprite.getMaxV()
            ) {
                layerSprites.add(sprite);
            }
        }
    }

    public void markActive(BlockRenderLayer layer) {
        Set<TextureAtlasSprite> layerSprites = this.sprites.get(layer);
        if (layerSprites == null) return;

        for (TextureAtlasSprite sprite : layerSprites) {
            if (Loader.isModLoaded("celeritas")) CeleritasTextureCompat.markActive(sprite);
            if (Loader.isModLoaded("loliasm")) ChibiTextureCompat.markActive(sprite);
        }
    }

    public void clear() {
        this.sprites.clear();
    }
}
