package org.valkyrienskies.mod.common.physics;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityShipSupport;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.ships.ship_world.WorldServerShipManager;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

/**
 * Immutable game-thread state consumed by an entity physics proxy.
 *
 * <p>Entity collision probing temporarily places entities in shipyard
 * coordinates. Capturing at the end of the game tick prevents the asynchronous
 * physics thread from observing that temporary state.</p>
 */
public record PhysicsEntitySnapshot(
        @NotNull Entity entity,
        @NotNull AxisAlignedBB boundingBox,
        @NotNull PhysicsEntityMovementQueue.MovementMarker movementMarker,
        @Nullable PhysicsObject supportingShip,
        boolean standingOnSupportingShip,
        boolean onGround,
        double verticalVelocity
) {
    @NotNull
    public static PhysicsEntitySnapshot capture(@NotNull Entity entity, @Nullable PhysicsObject potentialSupportingShip) {
        if (entity.world.isRemote) throw new IllegalStateException("Cannot create snapshot on the client!");
        PhysicsObject supportingShip = potentialSupportingShip != null && EntityShipSupport.hasRecentShipContact(entity, potentialSupportingShip.getShipData())
                ? potentialSupportingShip : null;
        boolean standingOnSupportingShip = supportingShip != null && EntityShipSupport.hasStandingContact(entity, supportingShip.getShipData());
        WorldServerShipManager serverShipManager = (WorldServerShipManager) ValkyrienUtils.getPhysObjWorld(entity.world);

        PhysicsEntityMovementQueue.MovementMarker movementMarker = serverShipManager != null
                        ? serverShipManager.getPhysicsLoop().capturePhysicsEntityMovementMarker(entity)
                        : PhysicsEntityMovementQueue.MovementMarker.NONE;

        return new PhysicsEntitySnapshot(
                entity,
                entity.getEntityBoundingBox(),
                movementMarker,
                supportingShip,
                standingOnSupportingShip,
                entity.onGround,
                entity.motionY
        );
    }
}
