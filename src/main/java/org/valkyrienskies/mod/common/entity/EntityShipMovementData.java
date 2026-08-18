package org.valkyrienskies.mod.common.entity;

import org.valkyrienskies.mod.common.ships.ShipData;

import javax.annotation.Nullable;

/**
 * Stores contact information used by ship-aware movement, rendering, and networking.
 */
public class EntityShipMovementData {
    // If null, then the last touched "Ship" was the world. Otherwise, the last touched ship was a real ship.
    @Nullable
    private ShipData lastTouchedShip;
    private int ticksSinceTouchedShip;
    // The number of consecutive ticks that lastTouchedShip has equaled null.
    private int ticksPartOfGround;
    // True only when ship collision resolution found a surface normal the entity can stand on.
    private boolean standingOnShip;

    public EntityShipMovementData(@Nullable ShipData lastTouchedShip, int ticksSinceTouchedShip, int ticksPartOfGround) {
        this(lastTouchedShip, ticksSinceTouchedShip, ticksPartOfGround, false);
    }

    public EntityShipMovementData(@Nullable ShipData lastTouchedShip, int ticksSinceTouchedShip, int ticksPartOfGround, boolean standingOnShip) {
        this.lastTouchedShip = lastTouchedShip;
        this.ticksSinceTouchedShip = ticksSinceTouchedShip;
        this.ticksPartOfGround = ticksPartOfGround;
        this.standingOnShip = standingOnShip && lastTouchedShip != null;
    }

    @Nullable
    public ShipData getLastTouchedShip() {
        return this.lastTouchedShip;
    }

    public int getTicksSinceTouchedShip() {
        return this.ticksSinceTouchedShip;
    }

    public int getTicksPartOfGround() {
        return this.ticksPartOfGround;
    }

    public boolean isStandingOnShip() {
        return this.standingOnShip;
    }

    public void setLastTouchedShip(@Nullable ShipData value) {
        boolean sameShip = value != null && this.lastTouchedShip != null && value.getUuid().equals(this.lastTouchedShip.getUuid());
        this.lastTouchedShip = value;
        this.standingOnShip = sameShip && this.standingOnShip;
    }

    public void setTicksSinceTouchedShip(int value) {
        this.ticksSinceTouchedShip = value;
    }

    public void setTicksPartOfGround(int value) {
        this.ticksPartOfGround = value;
    }

    public void setStandingOnShip(boolean value) {
        this.standingOnShip = value && this.lastTouchedShip != null;
    }
}
