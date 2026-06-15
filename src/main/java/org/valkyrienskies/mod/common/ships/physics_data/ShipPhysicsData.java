package org.valkyrienskies.mod.common.ships.physics_data;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.physics.PhysicsCalculations;

/**
 * Stores data used by {@link PhysicsCalculations}
 */
public class ShipPhysicsData {
    @JsonSerialize(as = Vector3d.class)
    @JsonDeserialize(as = Vector3d.class)
    private Vector3dc linearVelocity;
    @JsonSerialize(as = Vector3d.class)
    @JsonDeserialize(as = Vector3d.class)
    private Vector3dc angularVelocity;
    private transient volatile boolean collisionShapeDirty = true;

    public ShipPhysicsData() {}

    public ShipPhysicsData(Vector3dc linearVelocity, Vector3dc angularVelocity) {
        this.linearVelocity = linearVelocity;
        this.angularVelocity = angularVelocity;
    }

    public Vector3dc getLinearVelocity() {
        return this.linearVelocity;
    }

    public void setLinearVelocity(Vector3dc linearVelocity) {
        this.linearVelocity = linearVelocity;
    }

    public Vector3dc getAngularVelocity() {
        return this.angularVelocity;
    }

    public void setAngularVelocity(Vector3dc angularVelocity) {
        this.angularVelocity = angularVelocity;
    }

    public void markCollisionShapeDirty() {
        this.collisionShapeDirty = true;
    }

    public boolean consumeCollisionShapeDirty() {
        boolean result = this.collisionShapeDirty;
        this.collisionShapeDirty = false;
        return result;
    }
}
