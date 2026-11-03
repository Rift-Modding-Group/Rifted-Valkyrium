package org.valkyrienskies.mod.common.ships.block_relocation;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.world.World;
import org.valkyrienskies.mod.common.physics.BlockPhysicsDetails;

public class ShipBlockPosFinder extends SpatialDetector {
    private final MutableBlockPos mutablePos = new MutableBlockPos();

    public ShipBlockPosFinder(BlockPos start, World worldIn, int maximum, boolean checkCorners) {
        super(start, worldIn, maximum, checkCorners);
        this.startDetection();
    }

    @Override
    public boolean isValidExpansion(int x, int y, int z) {
        this.mutablePos.setPos(x, y, z);
        return !BlockPhysicsDetails.blocksToNotPhysicsInfuse.contains(this.getBlockState(this.mutablePos).getBlock());
    }
}
