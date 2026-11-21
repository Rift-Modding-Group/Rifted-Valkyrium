package org.valkyrienskies.mod.common.ships;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.mod.common.physics.BlockPhysicsDetails;
import org.valkyrienskies.mod.common.ships.physics_data.BasicCenterOfMassProvider;
import org.valkyrienskies.mod.common.ships.physics_data.IPhysicsObjectCenterOfMassProvider;
import valkyrienwarfare.api.ShipBlockBlacklist;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A utility class that modifies ShipData.
 */
@ParametersAreNonnullByDefault
public final class ShipDataMethods {
    // Calculates the new center of mass and inertia matrices for ships after a block change.
    private static final IPhysicsObjectCenterOfMassProvider centerOfMassProvider = new BasicCenterOfMassProvider();
    private static final int[][] COLLISION_SHAPE_DIRTY_OFFSETS = {
            {0, 0, 0},
            {1, 0, 0},
            {-1, 0, 0},
            {0, 1, 0},
            {0, -1, 0},
            {0, 0, 1},
            {0, 0, -1}
    };

    private ShipDataMethods() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Creates block-aligned limits extending upward from the bottom-center assembly block.
     */
    @NotNull
    public static AxisAlignedBB createBuildBounds(BlockPos bottomCenter, int[] dimensions) {
        long minX = (long) bottomCenter.getX() - dimensions[0] / 2;
        long minY = bottomCenter.getY();
        long minZ = (long) bottomCenter.getZ() - dimensions[2] / 2;
        return new AxisAlignedBB(minX, minY, minZ,
                minX + dimensions[0], minY + dimensions[1], minZ + dimensions[2]);
    }

    public static boolean isWithinBuildBounds(@Nullable AxisAlignedBB bounds, BlockPos pos) {
        return bounds == null || (pos.getX() >= bounds.minX && pos.getX() < bounds.maxX
                && pos.getY() >= bounds.minY && pos.getY() < bounds.maxY
                && pos.getZ() >= bounds.minZ && pos.getZ() < bounds.maxZ);
    }

    /**
     * Checks ship placement limits before changing blocks or physics data, always allowing removal.
     */
    public static boolean canSetBlockState(ShipData shipData, World world, BlockPos pos, IBlockState state) {
        if (state.getMaterial() == Material.AIR) return true;
        if (!isWithinBuildBounds(shipData.shipBounds, pos)) return false;
        if (shipData.assembledBlockBlacklistId == null) return true;
        ShipBlockBlacklist blacklist = ShipBlockBlacklistRegistry.get(shipData.assembledBlockBlacklistId);
        return blacklist != null && !blacklist.isBlacklisted(world, pos.toImmutable(), state);
    }

    /**
     * Updates the physics data/force positions of shipData.
     */
    public static void onSetBlockState(ShipData shipData, BlockPos pos, IBlockState oldState, IBlockState newState) {
        // Make sure that pos is even part of this ship
        if (!shipData.getChunkClaim().containsBlock(pos)) {
            throw new IllegalArgumentException("Get onSetBlockState() called for pos " + pos
                    + ", but this ISN'T a part of the ship " + shipData);
        }

        if (shipData.getBlockPositions() == null || shipData.getActiveForcePositions() == null) return;

        if (newState.equals(Blocks.AIR.getDefaultState())) {
            shipData.getBlockPositions().remove(pos);
        }
        else {
            shipData.getBlockPositions().add(pos);
        }

        if (BlockPhysicsDetails.isBlockProvidingForce(newState)) {
            shipData.getActiveForcePositions().add(pos);
        }
        else {
            shipData.getActiveForcePositions().remove(pos);
        }

        centerOfMassProvider.onSetBlockState(shipData.getInertiaData(), pos, oldState, newState);
        for (int[] offset : COLLISION_SHAPE_DIRTY_OFFSETS) {
            shipData.getPhysicsData().markCollisionShapeDirty(pos.add(offset[0], offset[1], offset[2]));
        }
    }
}
