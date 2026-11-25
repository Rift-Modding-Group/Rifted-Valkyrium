package org.valkyrienskies.mixin.mod.securitycraft;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.geforcemods.securitycraft.entity.camera.SecurityCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.common.Loader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityPlayerSP.class)
public abstract class MixinCameraPlayerMovement {
    //keep the physical player's ship-relative movement synchronized during remote viewing
    @ModifyExpressionValue(
            method = "onUpdateWalkingPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/entity/EntityPlayerSP;isCurrentViewEntity()Z"
            )
    )
    private boolean keepPlayerMovementWhileViewingCamera(boolean isCurrentViewEntity) {
        if (!Loader.isModLoaded("securitycraft")) return isCurrentViewEntity;
        return isCurrentViewEntity || Minecraft.getMinecraft().getRenderViewEntity() instanceof SecurityCamera;
    }
}
