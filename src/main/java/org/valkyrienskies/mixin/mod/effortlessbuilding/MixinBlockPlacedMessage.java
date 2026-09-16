package org.valkyrienskies.mixin.mod.effortlessbuilding;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

@Pseudo
@Mixin(targets = "nl.requios.effortlessbuilding.network.BlockPlacedMessage", remap = false)
public abstract class MixinBlockPlacedMessage {
    @Shadow
    private BlockPos blockPos;

    @Shadow
    private Vec3d hitVec;

    /**
     * Make ray hit result return subspace hit vector instead of world space
     */
    @Inject(method = "<init>(Lnet/minecraft/util/math/RayTraceResult;Z)V", at = @At("RETURN"), remap = false)
    private void convertShipHitVector(RayTraceResult result, boolean placeStartPosition, CallbackInfo callbackInfo) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null || this.blockPos == null || this.hitVec == null) return;

        PhysicsObject ship = ValkyrienUtils.getPhysoManagingBlock(minecraft.world, this.blockPos).orElse(null);
        if (ship == null) return;

        this.hitVec = ship.getShipTransformationManager().getRenderTransform().transform(this.hitVec, TransformType.GLOBAL_TO_SUBSPACE);
    }
}
