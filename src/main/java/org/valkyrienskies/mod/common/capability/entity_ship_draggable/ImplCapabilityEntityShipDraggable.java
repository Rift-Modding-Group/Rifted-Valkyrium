package org.valkyrienskies.mod.common.capability.entity_ship_draggable;

import org.joml.Vector3d;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.valkyrienskies.mod.common.entity.EntityShipMovementData;

public class ImplCapabilityEntityShipDraggable implements IEntityShipDraggable {
    @Nullable
    private EntityShipMovementData entityShipMovementData = new EntityShipMovementData(null, 0, 0, new Vector3d(), 0);
    private int ticksInAirPocket = 0;

    @Override
    public @Nullable EntityShipMovementData getEntityShipMovementData() {
        return this.entityShipMovementData;
    }

    @Override
    public void setEntityShipMovementData(@Nullable EntityShipMovementData entityShipMovementData) {
        this.entityShipMovementData = entityShipMovementData;
    }

    @Override
    public boolean getInAirPocket() {
        return this.ticksInAirPocket > 0;
    }

    @Override
    public void setTicksAirPocket(int ticksInAirPocket) {
        this.ticksInAirPocket = ticksInAirPocket;
    }

    @Override
    public void decrementTicksAirPocket() {
        this.ticksInAirPocket--;
    }
}
