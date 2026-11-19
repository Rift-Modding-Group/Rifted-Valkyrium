package org.valkyrienskies.mod.common.ships.interpolation;

import net.minecraft.util.math.AxisAlignedBB;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * An interface that allows for different ship interpolation algorithms to be implemented easily.
 */
public interface ITransformInterpolator {
    /**
     * Sends the latest transform and AABB to the interpolator.
     */
    void onNewTransformPacket(@Nonnull ShipTransform newTransform, @Nonnull AxisAlignedBB newAABB);

    /**
     * Uses the supplied transform immediately, or smooths toward the latest packet when null.
     */
    void tickTransformInterpolator(@Nullable ShipTransform attachedTransform);

    /**
     * Returns the current smoothed transform.
     */
    @Nonnull
    ShipTransform getCurrentTickTransform();

    /**
     * Returns the current smoothed AxisAlignedBB.
     */
    @Nonnull
    AxisAlignedBB getCurrentAABB();

}
