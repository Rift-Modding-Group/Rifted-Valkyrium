package org.valkyrienskies.mod.common.compat;

import git.jbredwards.fluidlogged_api.api.capability.IFluidStateCapability;
import git.jbredwards.fluidlogged_api.api.network.MessageUtils;
import git.jbredwards.fluidlogged_api.api.util.FluidState;
import git.jbredwards.fluidlogged_api.api.util.FluidloggedUtils;
import git.jbredwards.fluidlogged_api.mod.FluidloggedAPI;
import git.jbredwards.fluidlogged_api.mod.common.message.SMessageSyncFluidState;
import git.jbredwards.fluidlogged_api.mod.common.message.SMessageSyncFluidStates;
import git.jbredwards.fluidlogged_api.mod.common.fluid.util.ISpecializedFluidNeighborInfo;
import git.jbredwards.fluidlogged_api.mod.common.fluid.util.impl.SpecializedFluidNeighborInfo;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fluids.BlockFluidFinite;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Helper for Fluidlogged
 */
public class FluidloggedAPICompat {
    /**
     * Copies stored fluid alongside a relocated block, including an empty source state.
     */
    public static void copyFluidState(
            @Nonnull Chunk sourceChunk, @Nonnull BlockPos sourcePos,
            @Nonnull Chunk destinationChunk, @Nonnull BlockPos destinationPos
    ) {
        FluidState fluidState = FluidState.getFromProvider(sourceChunk, sourcePos);
        IFluidStateCapability capability = Objects.requireNonNull(IFluidStateCapability.get(destinationChunk));
        if (!capability.getContainer(destinationPos.getY()).setFluidState(destinationPos, fluidState)) return;

        destinationChunk.markDirty();
        MessageUtils.sendToAllTracking(
                new SMessageSyncFluidState(destinationPos.toImmutable(), fluidState, true),
                destinationChunk, FluidloggedAPI.WRAPPER
        );
    }

    /**
     * Removes stored fluid from a block that has been moved out of its source chunk.
     */
    public static void clearFluidState(@Nonnull Chunk chunk, @Nonnull BlockPos pos) {
        if (FluidloggedUtils.setFluidState(chunk.getWorld(), pos, chunk.getBlockState(pos), FluidState.EMPTY, false, 2)) {
            chunk.markDirty();
        }
    }

    /**
     * Clears flow left behind by relocated sources while retaining independently supplied fluid.
     */
    public static void clearWorldFlow(@Nonnull World world, @Nonnull long[] movingPositions) {
        Set<BlockPos> movingBlocks = new HashSet<>();
        for (long packedPos : movingPositions) movingBlocks.add(BlockPos.fromLong(packedPos));
        Set<BlockPos> unsupportedFlow = findUnsupportedFlow(movingBlocks, pos -> {
            if (world.isOutsideBuildHeight(pos) || !world.isBlockLoaded(pos, false)) return null;
            FluidState state = FluidloggedUtils.getFluidState(world, pos);
            if (state.isEmpty() || !state.isValid() || state.getBlock() instanceof BlockFluidFinite) return null;

            IBlockState blockState = world.getBlockState(pos);
            int openFaces = 0;
            for (EnumFacing side : EnumFacing.values()) {
                if (FluidloggedUtils.canFluidFlow(world, pos, blockState, side)) openFaces |= 1 << side.getIndex();
            }
            boolean falling;
            int downLevel;
            ISpecializedFluidNeighborInfo neighbors;
            if (state.getBlock() instanceof BlockLiquid) {
                falling = state.getLevel() >= 8;
                downLevel = state.getLevel();
                if (!falling) downLevel += 8;
                neighbors = new SpecializedFluidNeighborInfo.Vanilla(world, pos, state, 0);
            }
            else {
                neighbors = new SpecializedFluidNeighborInfo.Forge(world, pos, state, 0);
                falling = neighbors.hasVerticalFlow(0, 0, 0);
                downLevel = state.getFlowCost(world);
            }
            boolean spreadsHorizontally = state.isSource()
                    || !neighbors.canFlowInto(0, 0, 0, downLevel, state.getDownDensityFace(), true, true);
            return new FlowState(
                    state.getFluid().getName(), state.getWrappedLevel(world), state.getFlowCost(world),
                    state.getDownDensityFace(), state.isSource(), falling, spreadsHorizontally, openFaces
            );
        });
        for (BlockPos pos : unsupportedFlow) {
            if (FluidloggedUtils.setFluidToAir(world, pos, world.getBlockState(pos), 2)) world.getChunk(pos).markDirty();
        }
    }

    /**
     * Activates copied fluids after the complete ship is installed in the world.
     */
    public static void resumeFluidTicks(@Nonnull PhysicsObject ship) {
        World world = ship.getWorld();
        for (BlockPos pos : ship.getBlockPositions()) {
            FluidState fluidState = FluidState.get(world, pos);
            if (fluidState.isEmpty()) continue;

            fluidState.getState().neighborChanged(world, pos, fluidState.getBlock(), pos);
            FluidState updatedFluidState = FluidState.get(world, pos);
            if (updatedFluidState.isEmpty()) continue;

            world.scheduleUpdate(pos, updatedFluidState.getBlock(), updatedFluidState.getBlock().tickRate(world));
        }
    }

    /**
     * Sends the fluid snapshot after the ship's terrain packet.
     */
    public static void sendChunkFluidStates(@Nonnull Chunk chunk, @Nonnull EntityPlayerMP player) {
        IFluidStateCapability capability = Objects.requireNonNull(IFluidStateCapability.get(chunk));
        FluidloggedAPI.WRAPPER.sendTo(new SMessageSyncFluidStates(chunk, capability), player);
    }

    //-----for clearing up world flowin liquid when assemblin-----
    /**
     * Finds connected flowing cells that lose their supply when the selected blocks move.
     * The state lookup returns null for empty or unavailable positions.
     */
    @Nonnull
    public static Set<BlockPos> findUnsupportedFlow(@Nonnull Set<BlockPos> movingBlocks, @Nonnull Function<BlockPos, FlowState> stateAt) {
        Map<BlockPos, FlowState> snapshot = new HashMap<>();
        Function<BlockPos, FlowState> cachedStateAt = pos -> {
            if (!snapshot.containsKey(pos)) snapshot.put(pos, stateAt.apply(pos));
            return snapshot.get(pos);
        };
        Map<BlockPos, FlowState> connectedFlow = new HashMap<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        for (BlockPos pos : movingBlocks) {
            FlowState state = cachedStateAt.apply(pos);
            if (state == null) continue;
            connectedFlow.put(pos, state);
            pending.add(pos);
        }

        // Snapshot the connected flow before any source or neighboring fluid is removed.
        while (!pending.isEmpty()) {
            BlockPos pos = pending.remove();
            FlowState state = connectedFlow.get(pos);
            for (EnumFacing side : EnumFacing.values()) {
                BlockPos neighborPos = pos.offset(side);
                FlowState neighbor = cachedStateAt.apply(neighborPos);
                if (neighbor == null || !connects(state, neighbor, side)) continue;
                if (neighbor.source() && !movingBlocks.contains(neighborPos)) continue;
                if (connectedFlow.putIfAbsent(neighborPos, neighbor) == null) pending.add(neighborPos);
            }
        }

        Set<BlockPos> supported = new HashSet<>();
        for (Map.Entry<BlockPos, FlowState> entry : connectedFlow.entrySet()) {
            BlockPos pos = entry.getKey();
            if (movingBlocks.contains(pos)) continue;
            for (EnumFacing side : EnumFacing.values()) {
                BlockPos neighborPos = pos.offset(side);
                FlowState neighbor = snapshot.get(neighborPos);
                if (neighbor == null || !neighbor.source() || movingBlocks.contains(neighborPos)) continue;
                if (canFeed(neighbor, entry.getValue(), side.getOpposite())) {
                    supported.add(pos);
                    pending.add(pos);
                    break;
                }
            }
        }

        // Remaining sources support flow in the density direction and toward weaker levels.
        while (!pending.isEmpty()) {
            BlockPos pos = pending.remove();
            FlowState state = connectedFlow.get(pos);
            for (EnumFacing side : EnumFacing.values()) {
                BlockPos neighborPos = pos.offset(side);
                FlowState neighbor = connectedFlow.get(neighborPos);
                if (neighbor == null || movingBlocks.contains(neighborPos)) continue;
                if (canFeed(state, neighbor, side) && supported.add(neighborPos)) pending.add(neighborPos);
            }
        }

        Set<BlockPos> unsupported = new HashSet<>(connectedFlow.keySet());
        unsupported.removeAll(movingBlocks);
        unsupported.removeAll(supported);
        return unsupported;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean connects(@Nonnull FlowState from, @Nonnull FlowState to, @Nonnull EnumFacing side) {
        return from.fluid().equals(to.fluid())
                && (from.openFaces() & (1 << side.getIndex())) != 0
                && (to.openFaces() & (1 << side.getOpposite().getIndex())) != 0;
    }

    private static boolean canFeed(@Nonnull FlowState from, @Nonnull FlowState to, @Nonnull EnumFacing side) {
        if (!connects(from, to, side) || side == from.down().getOpposite()) return false;
        if (side == from.down()) return true;
        if (!from.spreadsHorizontally()) return false;
        int minimumLevel = from.level() + from.flowCost();
        if (from.falling()) minimumLevel = from.flowCost();
        return to.level() >= minimumLevel;
    }

    public record FlowState(
            @Nonnull String fluid, int level, int flowCost, @Nonnull EnumFacing down,
            boolean source, boolean falling, boolean spreadsHorizontally, int openFaces
    ) {}
}
