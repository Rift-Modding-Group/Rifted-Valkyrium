package org.valkyrienskies.mod.common.block;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import valkyrienwarfare.api.IShipForces;

/**
 * @deprecated Use {@link valkyrienwarfare.api.IShipForcesInducer#applyForces(IShipForces)} moving forward, this mostly exists to be consistent with the other force providers in terms of deprecation. This interface only exists in Rifted Valkyrium.. so... uhh...
 * */
@Deprecated
public interface IBlockBuoyancyProvider {
    /**
     * Returns the upward buoyancy force this block applies, in newtons, when fully submerged.
     * Note that this is as just a double instead of a vector because buoyancy only directs upward,
     * a vector would not be useful for that.
     */
    double getBuoyancyForce(World world, BlockPos pos, IBlockState state, PhysicsObject physicsObject);
}
