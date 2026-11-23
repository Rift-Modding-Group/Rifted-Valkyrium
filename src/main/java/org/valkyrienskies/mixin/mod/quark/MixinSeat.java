package org.valkyrienskies.mixin.mod.quark;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

@Pseudo
@Mixin(targets = "vazkii.quark.vanity.feature.SitInStairs$Seat", remap = false)
public abstract class MixinSeat {
    /**
     * Keeps Quark's stair validation in the anchored ship's block coordinates.
     */
    @Inject(method = "getPosition", at = @At("HEAD"), cancellable = true, require = 1, remap = true)
    private void useShipAnchorPosition(CallbackInfoReturnable<BlockPos> callbackInfo) {
        BlockPos anchor = ValkyrienUtils.getShipAnchoredPosition((Entity) (Object) this);
        if (anchor != null) callbackInfo.setReturnValue(anchor);
    }
}
