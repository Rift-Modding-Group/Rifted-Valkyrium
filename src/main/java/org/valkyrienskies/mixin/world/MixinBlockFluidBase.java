package org.valkyrienskies.mixin.world;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fluids.BlockFluidBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.ships.fluids.ShipWaterVolume;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

import javax.annotation.ParametersAreNonnullByDefault;

@Mixin(BlockFluidBase.class)
@ParametersAreNonnullByDefault
public abstract class MixinBlockFluidBase {
    @Inject(method = "canDisplace", at = @At("HEAD"), cancellable = true, remap = false)
    private void limitShipFluidDownflow(IBlockAccess access, BlockPos pos, CallbackInfoReturnable<Boolean> callback) {
        if (!(access instanceof World world)) return;
        PhysicsObject ship = ValkyrienUtils.getPhysoManagingBlock(world, pos).orElse(null);
        if (ship == null) return;
        AxisAlignedBB bounds = ShipWaterVolume.getHullBounds(ship);
        if (bounds != null && pos.getY() < bounds.minY) callback.setReturnValue(false);
    }
}
