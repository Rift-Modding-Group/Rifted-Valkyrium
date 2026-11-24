package org.valkyrienskies.mod.common.compat;

import git.jbredwards.fluidlogged_api.api.capability.IFluidStateCapability;
import git.jbredwards.fluidlogged_api.api.event.FluidloggedEvent;
import git.jbredwards.fluidlogged_api.api.network.MessageUtils;
import git.jbredwards.fluidlogged_api.api.util.FluidState;
import git.jbredwards.fluidlogged_api.api.util.FluidloggedUtils;
import git.jbredwards.fluidlogged_api.mod.FluidloggedAPI;
import git.jbredwards.fluidlogged_api.mod.common.message.SMessageSyncFluidState;
import git.jbredwards.fluidlogged_api.mod.common.message.SMessageSyncFluidStates;
import git.jbredwards.fluidlogged_api.mod.common.fluid.util.ISpecializedFluidNeighborInfo;
import git.jbredwards.fluidlogged_api.mod.common.fluid.util.impl.SpecializedFluidNeighborInfo;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.IBlockAccess;
import net.minecraft.util.math.AxisAlignedBB;
import git.jbredwards.fluidlogged_api.api.world.IBlockAccessWrapper;
import org.valkyrienskies.mod.common.ships.fluids.ShipWaterVolume;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fluids.BlockFluidFinite;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.event.ShipFluidSupportEvent;
import valkyrienwarfare.api.event.ShipBlockRelocationEvent;
import valkyrienwarfare.api.event.ShipChunkSentEvent;

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
    @Nonnull
    public static IBlockState getFluidOrReal(@Nonnull World world, @Nonnull BlockPos pos) {
        return FluidloggedUtils.getFluidOrReal(world, pos);
    }

    public static boolean isWorldWaterMasked(@Nonnull IBlockAccess access, @Nonnull BlockPos pos, @Nonnull AxisAlignedBB bounds) {
        while (access instanceof IBlockAccessWrapper wrapper) access = wrapper.getWrapped();
        return access instanceof World world && ShipWaterVolume.isWorldWaterMasked(world, bounds, pos);
    }

    public static boolean canWaterFlow(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull IBlockState state, @Nonnull EnumFacing side) {
        return FluidloggedUtils.canFluidFlow(world, pos, state, side);
    }

    public static boolean canAdmitWater(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull IBlockState state) {
        FluidState current = FluidloggedUtils.getFluidState(world, pos);
        if (!current.isEmpty() && (current.getMaterial() != Material.WATER || current.isSource())) return false;
        return state.getMaterial() == Material.AIR || state.getMaterial() == Material.WATER
                || FluidloggedUtils.isStateFluidloggable(state, world, pos, FluidState.of(Blocks.WATER));
    }

    public static boolean admitWater(@Nonnull World world, @Nonnull BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (!canAdmitWater(world, pos, state)) return false;
        if (state.getMaterial() == Material.AIR || state.getMaterial() == Material.WATER) {
            return world.setBlockState(pos, Blocks.FLOWING_WATER.getDefaultState(), 3);
        }
        return FluidloggedUtils.setFluidState(world, pos, state, FluidState.of(Blocks.FLOWING_WATER), false, 3);
    }

    public static double getWaterHeight(@Nonnull World world, @Nonnull BlockPos pos, double fallback) {
        FluidState fluid = FluidloggedUtils.getFluidState(world, pos);
        if (fluid.isEmpty() || fluid.getMaterial() != Material.WATER) return fallback;
        return pos.getY() + fluid.getActualHeight(world, pos);
    }

    @SubscribeEvent
    public static void onFluidlogged(@Nonnull FluidloggedEvent event) {
        ValkyrienUtils.getPhysoManagingBlock(event.world, event.pos).ifPresent(ship -> ship.getWaterVolume().markDirty());
    }

    @SubscribeEvent
    public static void onFluidFlowSupport(@Nonnull ShipFluidSupportEvent event) {
        boolean supported = !event.getState().getMaterial().isLiquid() || isFlowSupported(event.getWorld(), event.getPos());
        event.setResult(supported ? Event.Result.ALLOW : Event.Result.DENY);
    }

    /**
     * Copies stored fluid with the requested supply checks and clears it from removed source blocks.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockRelocationPost(@Nonnull ShipBlockRelocationEvent.PostCopied event) {
        Chunk destinationChunk = event.getDestinationChunk();
        BlockPos destinationPos = event.getDestinationPos();
        if (event.isSourceRemoval()) {
            if (FluidloggedUtils.setFluidState(destinationChunk.getWorld(), destinationPos,
                    destinationChunk.getBlockState(destinationPos), FluidState.EMPTY, false, 2)
            ) {
                destinationChunk.markDirty();
            }
            return;
        }

        Chunk sourceChunk = event.getSourceChunk();
        BlockPos sourcePos = event.getSourcePos();
        FluidState fluidState = FluidState.getFromProvider(sourceChunk, sourcePos);
        if (event.requiresSourceSupport() && !fluidState.isEmpty() && !isFlowSupported(sourceChunk.getWorld(), sourcePos)) {
            fluidState = FluidState.EMPTY;
        }
        IFluidStateCapability capability = Objects.requireNonNull(IFluidStateCapability.get(destinationChunk));
        if (!capability.getContainer(destinationPos.getY()).setFluidState(destinationPos, fluidState)) return;

        destinationChunk.markDirty();
        MessageUtils.sendToAllTracking(
                new SMessageSyncFluidState(destinationPos.toImmutable(), fluidState, true),
                destinationChunk, FluidloggedAPI.WRAPPER
        );
    }

    /**
     * Checks fluid supply through both faces of each connection, including stored fluid.
     * Finite fluids retain their independent volume when relocated.
     */
    public static boolean isFlowSupported(@Nonnull World world, @Nonnull BlockPos origin) {
        FluidState originState = FluidloggedUtils.getFluidState(world, origin);
        if (originState.isEmpty() || !originState.isValid() || originState.isSource() || originState.getBlock() instanceof BlockFluidFinite) {
            return true;
        }
        return ValkyrienUtils.isLiquidFlowSupported(
                origin,
                pos -> {
                    if (world.isOutsideBuildHeight(pos)) return -1;
                    if (!world.isBlockLoaded(pos, false)) return -2;
                    FluidState state = FluidloggedUtils.getFluidState(world, pos);
                    if (state.isEmpty() || !state.isValid() || !FluidloggedUtils.isCompatibleFluid(originState, state)) return -1;
                    if (state.isSource()) return 0;
                    return Math.max(1, state.getLevel());
                },
                (pos, side) -> {
                    if (!FluidloggedUtils.canFluidFlow(world, pos, world.getBlockState(pos), side)) return false;
                    BlockPos neighbor = pos.offset(side);
                    if (!world.isBlockLoaded(neighbor, false)) return true;
                    return FluidloggedUtils.canFluidFlow(world, neighbor, world.getBlockState(neighbor), side.getOpposite());
                }
        );
    }

    /**
     * Clears flow left behind by relocated sources while retaining independently supplied fluid.
     */
    @SubscribeEvent
    public static void onBlockRelocationCleanup(@Nonnull ShipBlockRelocationEvent.Cleanup event) {
        World world = event.getWorld();
        Set<BlockPos> movingBlocks = event.getSourceBlocks();
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
    @SubscribeEvent
    public static void onBlockRelocation(@Nonnull ShipBlockRelocationEvent.PreCopied event) {
        if (!event.isAssembling()) return;

        World world = event.getWorld();
        event.afterRelocation(() -> {
            for (BlockPos source : event.getSourceBlocks()) {
                BlockPos pos = source.add(event.getOffset());
                FluidState fluidState = FluidState.get(world, pos);
                if (fluidState.isEmpty()) continue;

                fluidState.getState().neighborChanged(world, pos, fluidState.getBlock(), pos);
                FluidState updatedFluidState = FluidState.get(world, pos);
                if (updatedFluidState.isEmpty()) continue;

                world.scheduleUpdate(pos, updatedFluidState.getBlock(), updatedFluidState.getBlock().tickRate(world));
            }
        });
    }

    /**
     * Sends the fluid snapshot after the ship's terrain packet.
     */
    @SubscribeEvent
    public static void onShipChunkSent(@Nonnull ShipChunkSentEvent event) {
        Chunk chunk = event.getChunk();
        IFluidStateCapability capability = Objects.requireNonNull(IFluidStateCapability.get(chunk));
        FluidloggedAPI.WRAPPER.sendTo(new SMessageSyncFluidStates(chunk, capability), event.getPlayer());
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
