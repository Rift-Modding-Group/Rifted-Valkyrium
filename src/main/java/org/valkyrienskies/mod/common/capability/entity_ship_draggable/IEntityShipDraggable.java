package org.valkyrienskies.mod.common.capability.entity_ship_draggable;

import org.valkyrienskies.mod.common.entity.EntityShipMovementData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface IEntityShipDraggable {
    @Nullable
    EntityShipMovementData getEntityShipMovementData();

    boolean getInAirPocket();

    void setTicksAirPocket(int ticksInAirPocket);

    void decrementTicksAirPocket();
}
