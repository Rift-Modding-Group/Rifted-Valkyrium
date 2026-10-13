package org.valkyrienskies.mixin.mod.renderlib;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes hanging lights from Fairy Lights not rendering when RenderLib is installed.
 */
@Pseudo
@Mixin(targets = "meldexun.renderlib.integration.ValkyrienSkies", remap = false)
public abstract class MixinRenderLibValkyrienSkies {
    @Inject(method = "getAABB", at = @At("HEAD"), cancellable = true, require = 0)
    private static void useFairyLightsGlobalBounds(TileEntity tileEntity, CallbackInfoReturnable<AxisAlignedBB> callbackInfo) {
        if (!tileEntity.getClass().getName().equals("com.pau101.fairylights.server.block.entity.BlockEntityFastener")) return;
        callbackInfo.setReturnValue(tileEntity.getRenderBoundingBox());
    }
}
