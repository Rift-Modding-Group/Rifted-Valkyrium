package org.valkyrienskies.mixin.mod.effortlessbuilding;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.JOML;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

@Pseudo
@Mixin(targets = "nl.requios.effortlessbuilding.buildmode.buildmodes.Line", remap = false)
public abstract class MixinLine {
    @ModifyVariable(method = "findLine", at = @At("STORE"), index = 3, remap = false)
    private static Vec3d useShipLocalLook(
            Vec3d look, @Local(argsOnly = true) EntityPlayer player,
            @Local(argsOnly = true) BlockPos firstPosition
    ) {
        PhysicsObject ship = ValkyrienUtils.getPhysoManagingBlock(player.world, firstPosition).orElse(null);
        if (ship == null) return look;

        ShipTransform transform = player.world.isRemote
            ? ship.getShipTransformationManager().getRenderTransform()
            : ship.getShipTransformationManager().getCurrentTickTransform();
        Vector3d localLook = JOML.convert(look);
        transform.transformDirection(localLook, TransformType.GLOBAL_TO_SUBSPACE);
        return JOML.toMinecraft(localLook);
    }

    @ModifyVariable(method = "findLine", at = @At("STORE"), index = 4, remap = false)
    private static Vec3d useShipLocalEyePosition(
            Vec3d eyePosition, @Local(argsOnly = true) EntityPlayer player,
            @Local(argsOnly = true) BlockPos firstPosition
    ) {
        PhysicsObject ship = ValkyrienUtils.getPhysoManagingBlock(player.world, firstPosition).orElse(null);
        if (ship == null) return eyePosition;

        ShipTransform transform = player.world.isRemote
            ? ship.getShipTransformationManager().getRenderTransform()
            : ship.getShipTransformationManager().getCurrentTickTransform();
        Vector3d localEyePosition = JOML.convert(eyePosition);
        transform.transformPosition(localEyePosition, TransformType.GLOBAL_TO_SUBSPACE);
        return JOML.toMinecraft(localEyePosition);
    }
}
