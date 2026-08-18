package org.valkyrienskies.mod.common.entity;

import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.ships.ShipData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * This class stores data about the last ship an entity touched, as well as the velocity that ship added to the entity.
 */
public class EntityShipMovementData {
    // If null, then the last touched "Ship" was the world. Otherwise, the last touched ship was a real ship.
    @Nullable
    ShipData lastTouchedShip;
    int ticksSinceTouchedShip;
    // The number of consecutive ticks that lastTouchedShip has equaled null.
    int ticksPartOfGround;
    @Nonnull
    private final Vector3dc addedLinearVelocity;
    private final double addedYawVelocity;

    public EntityShipMovementData(@Nullable ShipData lastTouchedShip, int ticksSinceTouchedShip,
        int ticksPartOfGround, @Nonnull Vector3dc addedLinearVelocity, double addedYawVelocity) {
        this.lastTouchedShip = lastTouchedShip;
        this.ticksSinceTouchedShip = ticksSinceTouchedShip;
        this.ticksPartOfGround = ticksPartOfGround;
        this.addedLinearVelocity = Objects.requireNonNull(addedLinearVelocity, "addedLinearVelocity");
        this.addedYawVelocity = addedYawVelocity;
    }

    @Nullable
    public ShipData getLastTouchedShip() {
        return lastTouchedShip;
    }

    public int getTicksSinceTouchedShip() {
        return ticksSinceTouchedShip;
    }

    public int getTicksPartOfGround() {
        return ticksPartOfGround;
    }

    @Nonnull
    public Vector3dc getAddedLinearVelocity() {
        return addedLinearVelocity;
    }

    public double getAddedYawVelocity() {
        return addedYawVelocity;
    }

    public EntityShipMovementData withLastTouchedShip(@Nullable ShipData value) {
        return new EntityShipMovementData(value, ticksSinceTouchedShip, ticksPartOfGround,
            addedLinearVelocity, addedYawVelocity);
    }

    public EntityShipMovementData withTicksSinceTouchedShip(int value) {
        return new EntityShipMovementData(lastTouchedShip, value, ticksPartOfGround,
            addedLinearVelocity, addedYawVelocity);
    }

    public EntityShipMovementData withTicksPartOfGround(int value) {
        return new EntityShipMovementData(lastTouchedShip, ticksSinceTouchedShip, value,
            addedLinearVelocity, addedYawVelocity);
    }

    public EntityShipMovementData withAddedLinearVelocity(@Nonnull Vector3dc value) {
        return new EntityShipMovementData(lastTouchedShip, ticksSinceTouchedShip, ticksPartOfGround,
            value, addedYawVelocity);
    }

    public EntityShipMovementData withAddedYawVelocity(double value) {
        return new EntityShipMovementData(lastTouchedShip, ticksSinceTouchedShip, ticksPartOfGround,
            addedLinearVelocity, value);
    }
}
