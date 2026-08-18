package org.valkyrienskies.mod.common.capability.entity_ship_draggable;

import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.mod.common.entity.EntityShipMovementData;

public class ImplCapabilityEntityShipDraggable implements IEntityShipDraggable {
    @NotNull
    private final EntityShipMovementData entityShipMovementData = new EntityShipMovementData(null, 0, 0);
    private int ticksInAirPocket = 0;

    @Override
    @NotNull
    public EntityShipMovementData getEntityShipMovementData() {
        return this.entityShipMovementData;
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
