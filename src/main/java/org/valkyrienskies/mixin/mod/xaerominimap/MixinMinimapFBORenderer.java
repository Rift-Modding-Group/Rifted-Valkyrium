package org.valkyrienskies.mixin.mod.xaerominimap;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.client.compat.MinimapMod;
import org.valkyrienskies.mod.client.compat.ShipMinimapRenderer;
import org.valkyrienskies.mod.common.config.VSConfig;

@Pseudo
@Mixin(targets = "xaero.common.minimap.render.MinimapFBORenderer", remap = false)
public abstract class MixinMinimapFBORenderer {
    @Inject(method = "renderChunksToFBO", require = 1, at = {
            @At(value = "INVOKE", target = "Lxaero/common/graphics/ImprovedFramebuffer;unbindFramebuffer()V", ordinal = 0),
            @At(value = "INVOKE", target = "Lxaero/common/graphics/ImprovedFramebuffer;func_147609_e()V", ordinal = 0)
    })
    private void renderShips(
            CallbackInfo callback,
            @Local(argsOnly = true, ordinal = 0) int mapDimension,
            @Local(argsOnly = true, ordinal = 0) float partialTicks,
            @Local(argsOnly = true, ordinal = 2) boolean cave,
            @Local(name = "xFloored") int cameraX,
            @Local(name = "zFloored") int cameraZ,
            @Local(name = "radiusBlocks") double radius
    ) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!VSConfig.showShipInMinimapMods || mc.world == null || cave || mapDimension != mc.world.provider.getDimension()) return;
        ShipMinimapRenderer.INSTANCE.render(cameraX, cameraZ, partialTicks, radius, MinimapMod.XAERO, null);
    }
}