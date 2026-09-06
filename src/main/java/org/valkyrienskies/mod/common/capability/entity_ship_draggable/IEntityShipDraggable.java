package org.valkyrienskies.mod.common.capability.entity_ship_draggable;

import org.jetbrains.annotations.NotNull;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.ships.ShipData;

import javax.annotation.Nullable;

public interface IEntityShipDraggable {
    //---ship contact info---
    //if null, the entity touched the world
    @Nullable
    ShipData getLastTouchedShip();

    void setLastTouchedShip(@Nullable ShipData lastTouchedShip);

    int getTicksSinceTouchedShip();

    void setTicksSinceTouchedShip(int value);

    int getTicksPartOfGround();

    void setTicksPartOfGround(int value);

    //linear velocity transferred from ship to entity that made contact
    @NotNull
    Vector3dc getAddedLinearVelocity();

    void setAddedLinearVelocity(@NotNull Vector3dc vector);

    //yaw velocity transferred from ship to entity that made contact
    double getAddedYawVelocity();

    void setAddedYawVelocity(double value);

    //---server-controlled movement in ship coordinates---
    @Nullable
    ShipLocalEntityMovementData getShipLocalMovementData();

    @NotNull
    ShipLocalEntityMovementData getOrCreateShipLocalMovementData();

    //---other stuff---
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean getInAirPocket();

    void setTicksAirPocket(int ticksInAirPocket);

    void decrementTicksAirPocket();
}
