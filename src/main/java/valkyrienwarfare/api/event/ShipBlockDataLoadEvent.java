package valkyrienwarfare.api.event;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.eventhandler.Event;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Posted on the Forge event bus after a ship's initial chunk cache is populated on either side,
 * or after an arriving client chunk is added to that cache and before its renderer is updated.
 * This event is not cancellable.
 * <p>
 * Handlers can rebuild auxiliary block data from the available terrain. Initial dispatch happens
 * during ship construction, before its transforms and rendering are initialized. Client terrain
 * may still be incomplete, and the ship need not be registered with its world manager yet.
 */
public class ShipBlockDataLoadEvent extends Event {
    private final World world;
    private final Set<BlockPos> blockPositions;
    @Nullable
    private final Chunk arrivingChunk;

    @ApiStatus.Internal
    public ShipBlockDataLoadEvent(
            @NotNull World world, @Nullable Iterable<BlockPos> blockPositions, @Nullable Chunk arrivingChunk
    ) {
        this.world = Objects.requireNonNull(world, "world");
        Set<BlockPos> positions = new HashSet<>();
        if (blockPositions != null) {
            for (BlockPos position : blockPositions) positions.add(position.toImmutable());
        }
        this.blockPositions = Set.copyOf(positions);
        this.arrivingChunk = arrivingChunk;
    }

    @NotNull
    public World getWorld() {
        return this.world;
    }

    /**
     * Returns an immutable snapshot of all known non-air ship positions, including neighboring
     * chunks. It is empty when block-position metadata is unavailable, as it may be on clients.
     */
    @NotNull
    public Set<BlockPos> getBlockPositions() {
        return this.blockPositions;
    }

    /**
     * Returns the client chunk whose data should be rebuilt, or null for initial cache population.
     * Blocks in neighboring chunks may contribute data to this chunk.
     */
    @Nullable
    public Chunk getArrivingChunk() {
        return this.arrivingChunk;
    }
}
