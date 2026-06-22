package org.valkyrienskies.mod.common.block;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;

//note: buoyancy is a force, maybe put it in ShipData.activeForcePositions?
public interface IBlockBuoyancyProvider {
    /**
     * returns how many full water blocks this block displaces when fully submerged
     */
    double getDisplacedWaterVolume(World world, BlockPos pos, IBlockState state, PhysicsObject physicsObject);
}
