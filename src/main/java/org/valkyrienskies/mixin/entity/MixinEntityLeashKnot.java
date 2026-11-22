package org.valkyrienskies.mixin.entity;

import net.minecraft.entity.EntityLeashKnot;
import net.minecraft.util.math.AxisAlignedBB;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.anchored_mount.IShipAnchoredMount;

@Mixin(EntityLeashKnot.class)
public class MixinEntityLeashKnot {
    /**
     * Keeps the fence in ship coordinates while the knot follows its exact world position.
     */
    @Inject(method = "setPosition", at = @At("HEAD"), cancellable = true)
    private void positionShipLeashKnot(double x, double y, double z, @NotNull CallbackInfo callbackInfo) {
        EntityLeashKnot knot = (EntityLeashKnot) (Object) this;
        IShipAnchoredMount anchor = knot.getCapability(VSCapabilityRegistry.VS_SHIP_ANCHORED_MOUNT, null);
        if (anchor == null || !anchor.isAnchoredToShip()) return;

        knot.posX = x;
        knot.posY = y;
        knot.posZ = z;
        knot.setEntityBoundingBox(new AxisAlignedBB(
                x - 0.1875D, y - 0.125D, z - 0.1875D,
                x + 0.1875D, y + 0.375D, z + 0.1875D
        ));
        callbackInfo.cancel();
    }
}
