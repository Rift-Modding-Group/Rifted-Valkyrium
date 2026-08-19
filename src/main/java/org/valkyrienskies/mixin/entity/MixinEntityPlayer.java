package org.valkyrienskies.mixin.entity;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

import java.util.Optional;

@Mixin(EntityPlayer.class)
public class MixinEntityPlayer {
    /**
     * this exists to ensure that players are positioned properly when sleeping on a bed on a ship
     * */
    @Redirect(method = "trySleep", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/EntityPlayer;setPosition(DDD)V"))
    private void trySleep(EntityPlayer player, double x, double y, double z, BlockPos bedLocation) {
        Optional<PhysicsObject> ship = ValkyrienUtils.getPhysoManagingBlock(player.world, bedLocation);

        if (ship.isEmpty()) player.setPosition(x, y, z);
        else {
            Vector3d globalPos = new Vector3d(x, y, z);
            ship.get().getShipTransformationManager()
                    .getCurrentTickTransform()
                    .transformPosition(globalPos, TransformType.SUBSPACE_TO_GLOBAL);
            player.setPosition(globalPos.x, globalPos.y, globalPos.z);
        }
    }
}