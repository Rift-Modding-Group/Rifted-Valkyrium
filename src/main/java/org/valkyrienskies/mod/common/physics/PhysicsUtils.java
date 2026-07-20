package org.valkyrienskies.mod.common.physics;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.MaterialLiquid;
import net.minecraft.block.state.IBlockState;

public class PhysicsUtils {
    public static boolean isLiquid(IBlockState state) {
        return state.getBlock() instanceof BlockLiquid || state.getMaterial() instanceof MaterialLiquid;
    }
}
