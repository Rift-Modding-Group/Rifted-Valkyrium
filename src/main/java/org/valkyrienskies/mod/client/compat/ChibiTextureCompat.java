package org.valkyrienskies.mod.client.compat;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import zone.rong.loliasm.client.sprite.ondemand.IAnimatedSpriteActivator;
import zone.rong.loliasm.client.sprite.ondemand.IAnimatedSpritePrimer;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class ChibiTextureCompat {
    @Nullable
    public static TextureAtlasSprite findSprite(TextureMap atlas, float u, float v) {
        if (atlas instanceof IAnimatedSpritePrimer primer) return primer.getAnimatedSprite(u, v);
        return null;
    }

    public static void markActive(TextureAtlasSprite sprite) {
        if (sprite instanceof IAnimatedSpriteActivator activator) activator.setActive(true);
    }
}
