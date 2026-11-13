package org.valkyrienskies.mod.client.compat;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import org.taumc.celeritas.impl.extensions.SpriteExtension;
import org.taumc.celeritas.impl.extensions.TextureMapExtension;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class CeleritasTextureCompat {
    @Nullable
    public static TextureAtlasSprite findSprite(TextureMap atlas, float u, float v) {
        if (atlas instanceof TextureMapExtension extension) return extension.celeritas$findFromUV(u, v);
        return null;
    }

    public static void markActive(TextureAtlasSprite sprite) {
        if (sprite instanceof SpriteExtension extension) extension.celeritas$markActive();
    }
}
