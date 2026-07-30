package org.valkyrienskies.mod.client.entity_position;

import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.UUID;

/**
 * Latest server-authoritative ship-local target for a remote entity. Minecraft
 * still owns interpolation toward this target.
 */
public class ShipLocalEntityRenderData {
    private static final int EXPIRY_TICKS = 10;

    @NotNull
    private final UUID shipUuid;
    final Vector3d localTarget;
    private final long lastUpdateTick;

    public ShipLocalEntityRenderData(@NotNull UUID shipUuid, @NotNull Vector3dc localTarget, long updateTick) {
        this.shipUuid = shipUuid;
        this.localTarget = new Vector3d(localTarget);
        this.lastUpdateTick = updateTick;
    }

    @NotNull
    public UUID getShipUuid() {
        return this.shipUuid;
    }

    public boolean isExpired(World world) {
        return world.getTotalWorldTime() - this.lastUpdateTick > EXPIRY_TICKS;
    }
}
