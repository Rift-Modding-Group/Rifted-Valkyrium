package org.valkyrienskies.mixin.mod.securitycraft;

import org.jetbrains.annotations.NotNull;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import com.llamalad7.mixinextras.sugar.Local;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

@Pseudo
@Mixin(targets = "net.geforcemods.securitycraft.blocks.SecurityCameraBlock", remap = false)
public abstract class MixinSecurityCameraBlock {
    @ModifyArg(
            method = "mountCamera",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/WorldServer;getChunk(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/world/chunk/Chunk;",
                    remap = true
            )
    )
    private BlockPos loadWorldAroundShipCamera(@NotNull BlockPos pos, @Local WorldServer world) {
        PhysicsObject ship = ValkyrienUtils.getPhysoManagingBlock(world, pos).orElse(null);
        if (ship == null) return pos;
        Vec3d position = ship.transformVector(new Vec3d(pos).add(0.5D, 0.5D, 0.5D), TransformType.SUBSPACE_TO_GLOBAL);
        return new BlockPos(position);
    }
}
