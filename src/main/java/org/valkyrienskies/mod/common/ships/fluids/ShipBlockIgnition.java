package org.valkyrienskies.mod.common.ships.fluids;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.IFluidBlock;
import org.joml.Vector3d;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import valkyrienwarfare.api.TransformType;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.LinkedHashMap;
import java.util.Map;

@ParametersAreNonnullByDefault
public class ShipBlockIgnition {
    public static void tick(PhysicsObject ship) {
        World world = ship.getWorld();
        if (world.isRemote || !world.getGameRules().getBoolean("doFireTick") || world.getTotalWorldTime() % 20L != 0L) return;

        //evaluate blocks on ship
        ShipTransform transform = ship.getShipTransformationManager().getCurrentTickTransform();
        Map<BlockPos, BlockPos> ignitions = new LinkedHashMap<>();
        Vector3d sample = new Vector3d();
        for (BlockPos pos : ship.getBlockPositions()) {
            if (!world.isBlockLoaded(pos, false)) continue;

            IBlockState state = world.getBlockState(pos);
            for (EnumFacing face : EnumFacing.values()) {
                if (!state.getBlock().isFlammable(world, pos, face)) continue;

                BlockPos firePos = pos.offset(face);
                if (!ship.getChunkClaim().containsBlock(firePos) || world.isOutsideBuildHeight(firePos)
                        || !world.isBlockLoaded(firePos, false) || !world.isAirBlock(firePos)
                ) {
                    continue;
                }
                boolean fluidMasked = ship.getWaterVolume().getSnapshot().isMasked(
                        firePos.getX() + 0.5D, firePos.getY() + 0.5D, firePos.getZ() + 0.5D
                );

                //sample both sides of the face for shallow liquids and overlapping world blocks
                for (int point = 0; point < 11; point++) {
                    double distance;
                    if (point == 1) distance = 1D;
                    else if (point == 2) distance = 0D;
                    else if (point >= 7) distance = 0.499D;
                    else distance = 0.501D;
                    sample.set(
                            pos.getX() + 0.5D + face.getXOffset() * distance,
                            pos.getY() + 0.5D + face.getYOffset() * distance,
                            pos.getZ() + 0.5D + face.getZOffset() * distance
                    );
                    if (point >= 3) {
                        double u = ((point - 3) & 1) == 0 ? -0.499D : 0.499D;
                        double v = ((point - 3) & 2) == 0 ? -0.499D : 0.499D;
                        switch (face.getAxis()) {
                            case X -> sample.add(0D, u, v);
                            case Y -> sample.add(u, 0D, v);
                            case Z -> sample.add(u, v, 0D);
                        }
                    }
                    transform.transformPosition(sample, TransformType.SUBSPACE_TO_GLOBAL);
                    BlockPos worldPos = new BlockPos(sample.x, sample.y, sample.z);
                    if (world.isOutsideBuildHeight(worldPos) || !world.isBlockLoaded(worldPos, false)) continue;

                    //burning by fire
                    IBlockState worldState = world.getBlockState(worldPos);
                    boolean burning = worldState.getMaterial() == Material.FIRE;

                    //burning by liquids
                    if (!burning) {
                        if (fluidMasked) continue;

                        ShipWaterVolume.FluidSample liquid = ShipWaterVolume.fluidSample(world, worldPos);
                        if (liquid == null || !liquid.contains(sample.y)) continue;
                        IBlockState liquidState = liquid.state();
                        burning = liquidState.getMaterial() == Material.LAVA;
                        if (!burning) {
                            Fluid fluid;
                            if (liquidState.getBlock() instanceof IFluidBlock fluidBlock) {
                                fluid = fluidBlock.getFluid();
                            }
                            else fluid = FluidRegistry.lookupFluidForBlock(liquidState.getBlock());

                            //573 kelvin is to be the basis of burning
                            burning = fluid != null && !fluid.isGaseous(world, worldPos) && fluid.getTemperature(world, worldPos) >= 573;
                        }
                    }

                    if (burning && !world.isRainingAt(worldPos)) {
                        ignitions.putIfAbsent(firePos, worldPos);
                        break;
                    }
                }
            }
        }

        //place fire after iterating
        for (Map.Entry<BlockPos, BlockPos> ignition : ignitions.entrySet()) {
            BlockPos pos = ignition.getKey();
            if (!world.isAirBlock(pos) || !Blocks.FIRE.canPlaceBlockAt(world, pos)) continue;
            IBlockState fire = Blocks.FIRE.getDefaultState();
            if (world.getBlockState(ignition.getValue()).getMaterial() != Material.FIRE) {
                fire = ForgeEventFactory.fireFluidPlaceBlockEvent(world, pos, ignition.getValue(), fire);
            }
            world.setBlockState(pos, fire, 3);
        }
    }
}
