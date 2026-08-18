package org.valkyrienskies.mod.client.entity_position;

import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.UUID;

/**
 * Tracks ship-local render samples used to render entities with the ship transform.
 */
public class ShipLocalEntityRenderData {
    private static final int EXPIRY_TICKS = 10;

    @NotNull
    private UUID shipUuid;
    private final Vector3d previousLocalPosition;
    private final Vector3d currentLocalPosition;
    private UUID queuedShipUuid;
    private final Vector3d queuedLocalPosition;
    private boolean hasQueuedUpdate;
    private long lastUpdateTick;

    public ShipLocalEntityRenderData(@NotNull final UUID shipUuid, final Vector3dc localPosition, final long updateTick) {
        this.shipUuid = shipUuid;
        this.previousLocalPosition = new Vector3d(localPosition);
        this.currentLocalPosition = new Vector3d(localPosition);
        this.queuedLocalPosition = new Vector3d(localPosition);
        this.lastUpdateTick = updateTick;
    }

    //queues the next ship-local sample until the next client render-data promotion.
    public void queueUpdate(@NotNull final UUID newShipUuid, final Vector3dc newLocalPosition) {
        this.queuedShipUuid = newShipUuid;
        this.queuedLocalPosition.set(newLocalPosition);
        this.hasQueuedUpdate = true;
    }

    //promotes the queued sample into the interpolation window for this client tick.
    public void promoteQueuedUpdate(final long updateTick) {
        if (!this.hasQueuedUpdate) return;

        if (!this.shipUuid.equals(this.queuedShipUuid) || updateTick - this.lastUpdateTick > EXPIRY_TICKS) {
            this.previousLocalPosition.set(this.queuedLocalPosition);
        }
        else {
            this.previousLocalPosition.set(this.currentLocalPosition);
        }

        this.shipUuid = this.queuedShipUuid;
        this.currentLocalPosition.set(this.queuedLocalPosition);
        this.lastUpdateTick = updateTick;
        this.hasQueuedUpdate = false;
    }

    //returns the ship UUID that owns the current local render position.
    @NotNull
    public UUID getShipUuid() {
        return this.shipUuid;
    }

    //returns true once this render data has gone too long without an update.
    public boolean isExpired(final World world) {
        return world.getTotalWorldTime() - this.lastUpdateTick > EXPIRY_TICKS;
    }

    //returns the partial-tick interpolated ship-local render position.
    public Vector3d getInterpolatedLocalPosition(final double partialTicks) {
        final double clampedPartialTicks = Math.clamp(partialTicks, 0, 1);
        return this.previousLocalPosition.lerp(this.currentLocalPosition, clampedPartialTicks, new Vector3d());
    }
}
