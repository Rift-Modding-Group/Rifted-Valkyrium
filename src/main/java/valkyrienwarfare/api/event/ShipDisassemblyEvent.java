package valkyrienwarfare.api.event;

import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.Event;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import valkyrienwarfare.api.IPhysicsEntity;

import java.util.Objects;

/**
 * Posted on the Forge event bus on the server when a nonempty ship is ready to return
 * its blocks to terrain, after alignment and build-height checks and before relocation
 * handlers, block copying, or ship teardown. This does not fire for empty-ship cleanup
 * or deletion without copying blocks.
 * <p>
 * Cancelling keeps the ship assembled and clears its pending disassembly and alignment
 * request. A later disassembly request can post this event again.
 */
@Cancelable
public class ShipDisassemblyEvent extends Event {
    private final IPhysicsEntity ship;

    @ApiStatus.Internal
    public ShipDisassemblyEvent(@NotNull IPhysicsEntity ship) {
        this.ship = Objects.requireNonNull(ship, "ship");
    }

    @NotNull
    public World getWorld() {
        return this.ship.getWorld();
    }

    @NotNull
    public IPhysicsEntity getShip() {
        return this.ship;
    }
}
