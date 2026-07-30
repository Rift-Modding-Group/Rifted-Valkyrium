package org.valkyrienskies.mixin.client.entity;

import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.client.entity_position.EntityRenderPositionManager;

/**
 * Keeps vanilla's remote-entity interpolation target in the same world frame
 * as its supporting ship. Carrier motion must never modify yaw or pitch.
 */
@Mixin(EntityLivingBase.class)
public abstract class MixinEntityLivingBaseClient {
    @Inject(method = "setPositionAndRotationDirect", at = @At("RETURN"))
    private void restoreShipLocalPositionTarget(
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            int posRotationIncrements,
            boolean teleport,
            CallbackInfo callbackInfo
    ) {
        EntityRenderPositionManager.remapRemoteInterpolationTarget(
                (EntityLivingBase) (Object) this
        );
    }
}
