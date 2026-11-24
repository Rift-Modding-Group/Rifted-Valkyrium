package org.valkyrienskies.mixin.client.renderer;

import net.minecraft.block.state.IBlockState;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.common.Loader;
import org.joml.Vector3d;
import org.valkyrienskies.mod.common.compat.FluidloggedAPICompat;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;
import org.valkyrienskies.mod.common.ships.fluids.ShipWaterVolume;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;

@Mixin(ActiveRenderInfo.class)
public class MixinActiveRenderInfo {
    @Inject(method = "getBlockStateAtEntityViewpoint", at = @At("RETURN"), cancellable = true)
    private static void onGetBlockStateAtEntityViewpoint(World worldIn, Entity entityIn, float p_186703_2_, CallbackInfoReturnable<IBlockState> cir) {
        IEntityShipDraggable entityShipDraggable = entityIn.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
        Vec3d viewpoint = ActiveRenderInfo.projectViewFromEntity(entityIn, p_186703_2_);
        boolean pumped = entityShipDraggable != null && entityShipDraggable.getInAirPocket();
        if (pumped) {
            cir.setReturnValue(Blocks.AIR.getDefaultState());
            return;
        }
        IPhysObjectWorld manager = ValkyrienUtils.getPhysObjWorld(worldIn);
        if (manager == null) return;
        AxisAlignedBB eyeBounds = new AxisAlignedBB(viewpoint.x, viewpoint.y, viewpoint.z, viewpoint.x, viewpoint.y, viewpoint.z).grow(0.001D);
        for (PhysicsObject ship : manager.getPhysObjectsInAABB(eyeBounds)) {
            Vector3d local = new Vector3d(viewpoint.x, viewpoint.y, viewpoint.z);
            ship.getShipTransformationManager().getRenderTransform().transformPosition(local, TransformType.GLOBAL_TO_SUBSPACE);
            if (!ship.getWaterVolume().getSnapshot().isMasked(local.x, local.y, local.z)) continue;
            BlockPos pos = new BlockPos(local.x, local.y, local.z);
            IBlockState state = worldIn.getBlockState(pos);
            if (Loader.isModLoaded("fluidlogged_api")) state = FluidloggedAPICompat.getFluidOrReal(worldIn, pos);
            if (state.getMaterial() == Material.WATER && ShipWaterVolume.waterHeight(worldIn, local.x, local.y, local.z) > local.y) {
                cir.setReturnValue(state);
            }
            else if (cir.getReturnValue().getMaterial() == Material.WATER) cir.setReturnValue(Blocks.AIR.getDefaultState());
            return;
        }
    }
}
