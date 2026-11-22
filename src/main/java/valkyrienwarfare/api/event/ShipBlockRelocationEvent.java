package valkyrienwarfare.api.event;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.eventhandler.Event;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Forge events for source terrain cleanup ({@link Cleanup}), relocating a selected set of source
 * blocks ({@link PreCopied}), and writing destination or cleared source block states ({@link PostCopied}).
 * During assembly, block states are copied before cleanup and preparation for source removal.
 */
public abstract class ShipBlockRelocationEvent extends Event {
    /**
     * Posted on the Forge event bus on the server during assembly, after all destination
     * blocks have been copied but before {@link PreCopied} and before source blocks are removed.
     * The destination chunks are not installed in the world yet.
     * <p>
     * Handlers can clean up terrain that depends on the selected source blocks, such as
     * fluid losing its supply. Source blocks and their stored data must remain available
     * for relocation. This event is not cancellable and does not have a result.
     */
    public static class Cleanup extends ShipBlockRelocationEvent {
        private final World world;
        private final Set<BlockPos> sourceBlocks;

        @ApiStatus.Internal
        public Cleanup(@NotNull World world, @NotNull Iterable<BlockPos> sourceBlocks) {
            this.world = Objects.requireNonNull(world, "world");
            Set<BlockPos> positions = new HashSet<>();
            for (BlockPos position : sourceBlocks) positions.add(position.toImmutable());
            this.sourceBlocks = Set.copyOf(positions);
        }

        @NotNull
        public World getWorld() {
            return this.world;
        }

        /** Returns an immutable snapshot of source positions in terrain coordinates. */
        @NotNull
        public Set<BlockPos> getSourceBlocks() {
            return this.sourceBlocks;
        }
    }

    /**
     * Posted on the Forge event bus on the server before selected source blocks are removed during
     * assembly, or before they are copied during disassembly. This event is not cancellable.
     * <p>
     * Capability handlers can select records belonging to {@link #getSourceBlocks()}, capture them,
     * and register a restoration action with {@link #afterRelocation(Runnable)}. Destination positions
     * are source positions plus {@link #getOffset()}; arbitrary capability data is not copied implicitly.
     */
    public static class PreCopied extends ShipBlockRelocationEvent {
        private final World world;
        private final Set<BlockPos> sourceBlocks;
        private final BlockPos offset;
        private final boolean assembling;
        private final Consumer<Runnable> restorationActions;

        @ApiStatus.Internal
        public PreCopied(
                @NotNull World world, @NotNull Iterable<BlockPos> sourceBlocks,
                @NotNull BlockPos offset, boolean assembling, @NotNull Consumer<Runnable> restorationActions
        ) {
            this.world = Objects.requireNonNull(world, "world");
            Set<BlockPos> positions = new HashSet<>();
            for (BlockPos position : sourceBlocks) positions.add(position.toImmutable());
            this.sourceBlocks = Set.copyOf(positions);
            this.offset = offset.toImmutable();
            this.assembling = assembling;
            this.restorationActions = Objects.requireNonNull(restorationActions, "restorationActions");
        }

        @NotNull
        public World getWorld() {
            return this.world;
        }

        /**
         * Returns an immutable snapshot of the selected source positions.
         * */
        @NotNull
        public Set<BlockPos> getSourceBlocks() {
            return this.sourceBlocks;
        }

        @NotNull
        public BlockPos getOffset() {
            return this.offset;
        }

        /** Returns true when moving blocks into a ship, or false when returning them to terrain. */
        public boolean isAssembling() {
            return this.assembling;
        }

        /**
         * Registers an action during event dispatch. It runs on the server after destination blocks
         * and chunks are available, and after the destination ship is registered when assembling.
         * Handlers remain responsible for updating their registries, marking storage dirty and syncing.
         * All restoration actions are attempted before any runtime exceptions are propagated.
         */
        public void afterRelocation(@NotNull Runnable action) {
            this.restorationActions.accept(Objects.requireNonNull(action, "action"));
        }
    }

    /**
     * Posted on the Forge event bus immediately after a destination block state is copied or an
     * assembly source block is cleared, before its tile entity is copied or removed. Copy notifications
     * also precede destination block updates. This event is not cancellable.
     * <p>
     * When {@link #isSourceRemoval()} is true, both positions and chunks identify the cleared source.
     * Otherwise, the source still contains the original block and handlers can copy auxiliary data
     * between the supplied chunks. During assembly the destination chunk is not installed in the world yet;
     * handlers must not look it up through the world or assume other destination blocks are ready.
     * Use {@link PreCopied#afterRelocation(Runnable)} for work requiring the full ship.
     */
    public static class PostCopied extends ShipBlockRelocationEvent {
        private final Chunk sourceChunk;
        private final BlockPos sourcePos;
        private final Chunk destinationChunk;
        private final BlockPos destinationPos;
        private final IBlockState oldState;
        private final IBlockState newState;
        private final boolean requiresSourceSupport;
        private final boolean sourceRemoval;

        @ApiStatus.Internal
        public PostCopied(
                @NotNull Chunk sourceChunk, @NotNull BlockPos sourcePos,
                @NotNull Chunk destinationChunk, @NotNull BlockPos destinationPos,
                @NotNull IBlockState oldState, @NotNull IBlockState newState,
                boolean requiresSourceSupport, boolean sourceRemoval
        ) {
            this.sourceChunk = Objects.requireNonNull(sourceChunk, "sourceChunk");
            this.sourcePos = sourcePos.toImmutable();
            this.destinationChunk = Objects.requireNonNull(destinationChunk, "destinationChunk");
            this.destinationPos = destinationPos.toImmutable();
            this.oldState = Objects.requireNonNull(oldState, "oldState");
            this.newState = Objects.requireNonNull(newState, "newState");
            this.requiresSourceSupport = requiresSourceSupport;
            this.sourceRemoval = sourceRemoval;
        }

        @NotNull
        public Chunk getSourceChunk() {
            return this.sourceChunk;
        }

        @NotNull
        public BlockPos getSourcePos() {
            return this.sourcePos;
        }

        @NotNull
        public Chunk getDestinationChunk() {
            return this.destinationChunk;
        }

        @NotNull
        public BlockPos getDestinationPos() {
            return this.destinationPos;
        }

        /**
         * Returns the destination block state before it was copied or cleared.
         * */
        @NotNull
        public IBlockState getOldState() {
            return this.oldState;
        }

        /**
         * Returns the destination block state after it was copied or cleared.
         * */
        @NotNull
        public IBlockState getNewState() {
            return this.newState;
        }

        /**
         * Whether source-dependent data, such as flowing fluid, must have a supply at the source.
         * This is required when copying a ship back into terrain during disassembly.
         */
        public boolean requiresSourceSupport() {
            return this.requiresSourceSupport;
        }

        /**
         * Returns true after an assembly source block has been cleared.
         * */
        public boolean isSourceRemoval() {
            return this.sourceRemoval;
        }
    }
}
