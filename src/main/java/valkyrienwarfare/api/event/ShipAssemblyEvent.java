package valkyrienwarfare.api.event;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.Event;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import valkyrienwarfare.api.RiftedValkyriumAPI;
import valkyrienwarfare.api.ShipBuildOptions;

import java.util.Objects;

/**
 * Posted on the Forge event bus on the server when {@link RiftedValkyriumAPI#createNewShip}
 * is called, before allocating a chunk claim, detecting blocks, or queuing assembly.
 * Cancelling returns null from that request without creating a ship.
 * <p>
 * Handlers can modify {@link #getShipBuildOptions()}. The final options apply to block selection
 * and all other build settings. Accepting the event does not bypass later assembly checks.
 */
@Cancelable
public class ShipAssemblyEvent extends Event {
    private final World world;
    private final Vec3d initialPosition;
    @Nullable
    private final AxisAlignedBB initialBounds;
    private final ShipBuildOptions shipBuildOptions;

    @ApiStatus.Internal
    public ShipAssemblyEvent(
            @NotNull World world, @NotNull Vec3d initialPosition,
            @Nullable AxisAlignedBB initialBounds, @NotNull ShipBuildOptions shipBuildOptions
    ) {
        this.world = Objects.requireNonNull(world, "world");
        this.initialPosition = Objects.requireNonNull(initialPosition, "initialPosition");
        this.initialBounds = initialBounds;
        this.shipBuildOptions = Objects.requireNonNull(shipBuildOptions, "shipBuildOptions");
    }

    @NotNull
    public World getWorld() {
        return this.world;
    }

    @NotNull
    public Vec3d getInitialPosition() {
        return this.initialPosition;
    }

    /**
     * Returns the terrain selection bounds, or null for connected-block assembly.
     * */
    @Nullable
    public AxisAlignedBB getInitialBounds() {
        return this.initialBounds;
    }

    /**
     * Returns the mutable options that will be used if assembly is accepted.
     * */
    @NotNull
    public ShipBuildOptions getShipBuildOptions() {
        return this.shipBuildOptions;
    }
}
