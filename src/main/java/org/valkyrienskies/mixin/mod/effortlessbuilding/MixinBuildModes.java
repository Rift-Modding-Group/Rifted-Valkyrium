package org.valkyrienskies.mixin.mod.effortlessbuilding;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

@Pseudo
@Mixin(targets = "nl.requios.effortlessbuilding.buildmode.BuildModes", remap = false)
public abstract class MixinBuildModes {
    /**
     * turn subspace start position into global before measuring its distance from the player
     */
    @ModifyExpressionValue(
        method = "onBlockPlacedMessage",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/BlockPos;distanceSq(Lnet/minecraft/util/math/Vec3i;)D"),
        remap = false
    )
    private static double useGlobalShipDistance(
            double originalDistance,
            @Local(argsOnly = true) EntityPlayer player,
            @Local(name = "startPos") BlockPos startPosition
    ) {
        PhysicsObject ship = ValkyrienUtils.getPhysoManagingBlock(player.world, startPosition).orElse(null);
        if (ship == null) return originalDistance;

        ShipTransform transform = player.world.isRemote
            ? ship.getShipTransformationManager().getRenderTransform()
            : ship.getShipTransformationManager().getCurrentTickTransform();
        Vector3d globalPosition = new Vector3d(startPosition.getX(), startPosition.getY(), startPosition.getZ());
        transform.transformPosition(globalPosition, TransformType.SUBSPACE_TO_GLOBAL);
        return player.getPosition().distanceSq(globalPosition.x, globalPosition.y, globalPosition.z);
    }
}
