package org.valkyrienskies.mod.common.ships.block_relocation;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.world.World;
import valkyrienwarfare.api.ShipBlockBlacklist;

public class ShipSpawnDetector extends SpatialDetector {
    private final MutableBlockPos mutablePos = new MutableBlockPos();
    private final ShipBlockBlacklist blockBlacklist;

    ShipSpawnDetector(
            BlockPos start, World worldIn, int maximum, boolean checkCorners,
            ShipBlockBlacklist blockBlacklist
    ) {
        super(start, worldIn, maximum, checkCorners);
        this.blockBlacklist = blockBlacklist;
        startDetection();
    }

    @Override
    public boolean isValidExpansion(int x, int y, int z) {
        this.mutablePos.setPos(x, y, z);
        IBlockState state = this.getBlockState(this.mutablePos);
        return !this.blockBlacklist.isBlacklisted(
                this.worldObj, this.mutablePos.toImmutable(), state
        );
    }
}