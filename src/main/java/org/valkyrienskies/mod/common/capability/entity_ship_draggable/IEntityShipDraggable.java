package org.valkyrienskies.mod.common.capability.entity_ship_draggable;

import org.valkyrienskies.mod.common.ships.ShipData;

import javax.annotation.Nullable;

public interface IEntityShipDraggable {

    @Nullable
    ShipData getLastTouchedShip();

    int getTicksSinceTouchedShip();

    int getTicksPartOfGround();

    boolean isStandingOnShip();

    void setLastTouchedShip(@Nullable ShipData lastTouchedShip);

    void setTicksSinceTouchedShip(int ticksSinceTouchedShip);

    void setTicksPartOfGround(int ticksPartOfGround);

    void setStandingOnShip(boolean standingOnShip);

    boolean getInAirPocket();

    void setTicksAirPocket(int ticksInAirPocket);

    void decrementTicksAirPocket();
}
