package org.valkyrienskies.mod.common.block;

import net.minecraft.util.math.BlockPos;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.physics.PhysicsCalculations;
import valkyrienwarfare.api.IShipForces;

import javax.annotation.Nullable;

/**
 * @deprecated Use {@link valkyrienwarfare.api.IShipForcesInducer#applyForces(IShipForces)} moving forward, this mostly exists to keep compat w old VS1 addons.
 * */
public interface IBlockTorqueProvider extends Comparable<IBlockTorqueProvider> {

    @Nullable
    Vector3dc getTorqueInGlobal(PhysicsCalculations physicsCalculations, BlockPos pos);

    default int compareTo(IBlockTorqueProvider other) {
        return getBlockSortingIndex() - other.getBlockSortingIndex();
    }

    /**
     * Lower numbers will put this Block at a higher priority for adding torque to the rigid body; a
     * higher number is used to put this Block at a lower priority.
     */
    default int getBlockSortingIndex() {
        return 0;
    }
}
