package org.valkyrienskies.mod.client.compat;

import git.jbredwards.fluidlogged_api.api.block.IFluidloggable;
import git.jbredwards.fluidlogged_api.api.util.FluidState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import javax.annotation.Nonnull;

public class FluidloggedRenderCompat {
    /**
     * Adds stored fluid geometry to the current ship render layer.
     */
    public static void renderFluidState(
            @Nonnull IBlockState state, @Nonnull BlockPos pos, @Nonnull IBlockAccess world,
            @Nonnull BufferBuilder buffer, @Nonnull BlockRenderLayer layer
    ) {
        FluidState fluidState = FluidState.get(world, pos);
        if (fluidState.isEmpty() || !fluidState.getBlock().canRenderInLayer(fluidState.getState(), layer)) return;
        if (state.getBlock() instanceof IFluidloggable fluidloggable && !fluidloggable.shouldFluidRender(world, pos, state, fluidState)) return;

        Minecraft.getMinecraft().getBlockRendererDispatcher().renderBlock(fluidState.getState(), pos, world, buffer);
    }
}