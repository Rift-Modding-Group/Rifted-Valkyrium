package org.valkyrienskies.mod.common.physics.bodies;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4dc;
import org.joml.Vector3d;

/**
 * Implement this on anything that handles collisions for entities.
 * */
public interface IPhysicsEntityBody {
    /**
     * transfer displacements from physX collisions to the game
     * is called by PhysicsEntityMovementQueue on the Minecraft thread
     * */
    @Nullable
    Vector3d applyPendingMovement(@NotNull Matrix4dc movementTransform);
}
