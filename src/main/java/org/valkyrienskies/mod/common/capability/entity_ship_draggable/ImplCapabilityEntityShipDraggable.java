package org.valkyrienskies.mod.common.capability.entity_ship_draggable;

import org.valkyrienskies.mod.common.ships.ShipData;

import javax.annotation.Nullable;

public class ImplCapabilityEntityShipDraggable implements IEntityShipDraggable {

    @Nullable
    private ShipData lastTouchedShip;
    private int ticksSinceTouchedShip;
    private int ticksPartOfGround;
    private boolean standingOnShip;
    private int ticksInAirPocket = 0;

    @Override
    @Nullable
    public ShipData getLastTouchedShip() {
        return this.lastTouchedShip;
    }

    @Override
    public int getTicksSinceTouchedShip() {
        return this.ticksSinceTouchedShip;
    }

    @Override
    public int getTicksPartOfGround() {
        return this.ticksPartOfGround;
    }

    @Override
    public boolean isStandingOnShip() {
        return this.standingOnShip;
    }

    @Override
    public void setLastTouchedShip(@Nullable ShipData lastTouchedShip) {
        boolean sameShip = lastTouchedShip != null
                && this.lastTouchedShip != null
                && lastTouchedShip.getUuid().equals(this.lastTouchedShip.getUuid());
        this.lastTouchedShip = lastTouchedShip;
        this.standingOnShip = sameShip && this.standingOnShip;
    }

    @Override
    public void setTicksSinceTouchedShip(int ticksSinceTouchedShip) {
        this.ticksSinceTouchedShip = ticksSinceTouchedShip;
    }

    @Override
    public void setTicksPartOfGround(int ticksPartOfGround) {
        this.ticksPartOfGround = ticksPartOfGround;
    }

    @Override
    public void setStandingOnShip(boolean standingOnShip) {
        this.standingOnShip = standingOnShip && this.lastTouchedShip != null;
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
