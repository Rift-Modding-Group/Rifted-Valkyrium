package org.valkyrienskies.mixin.entity;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityDraggable;
import org.valkyrienskies.mod.common.util.TransformedAABB;
import valkyrienwarfare.api.TransformType;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes boats resting on ship blocks use the same land status
 * and slipperiness as boats resting on ordinary terrain.
 */
@Mixin(EntityBoat.class)
public class MixinEntityBoat {
    @Inject(method = "getBoatGlide", at = @At("HEAD"), cancellable = true)
    private void useShipDeckGlide(@NotNull CallbackInfoReturnable<Float> callbackInfo) {
        EntityBoat boat = (EntityBoat) (Object) this;
        ShipData activeShip = EntityDraggable.getActiveShip(boat);
        if (activeShip == null) return;

        AxisAlignedBB localBoatBox = new TransformedAABB(
                boat.getEntityBoundingBox(), activeShip.getShipTransform(), TransformType.GLOBAL_TO_SUBSPACE
        ).getEnclosedAABB();
        AxisAlignedBB supportArea = new AxisAlignedBB(
                localBoatBox.minX, localBoatBox.minY - 0.05D, localBoatBox.minZ,
                localBoatBox.maxX, localBoatBox.minY, localBoatBox.maxZ
        );
        int minX = MathHelper.floor(supportArea.minX) - 1;
        int maxX = MathHelper.ceil(supportArea.maxX) + 1;
        int minY = MathHelper.floor(supportArea.minY) - 1;
        int maxY = MathHelper.ceil(supportArea.maxY) + 1;
        int minZ = MathHelper.floor(supportArea.minZ) - 1;
        int maxZ = MathHelper.ceil(supportArea.maxZ) + 1;
        List<AxisAlignedBB> collisionBoxes = new ArrayList<>();
        float totalSlipperiness = 0f;
        int supportingBlocks = 0;

        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                for (int y = minY; y < maxY; y++) {
                    BlockPos blockPosition = new BlockPos(x, y, z);
                    IBlockState blockState = boat.world.getBlockState(blockPosition);
                    collisionBoxes.clear();
                    blockState.addCollisionBoxToList(boat.world, blockPosition, supportArea, collisionBoxes, boat, false);
                    if (collisionBoxes.isEmpty()) continue;

                    totalSlipperiness += blockState.getBlock().getSlipperiness(blockState, boat.world, blockPosition, boat);
                    supportingBlocks++;
                }
            }
        }

        if (supportingBlocks > 0) callbackInfo.setReturnValue(totalSlipperiness / supportingBlocks);
        else if (boat.onGround) callbackInfo.setReturnValue(0.6f);
    }
}
