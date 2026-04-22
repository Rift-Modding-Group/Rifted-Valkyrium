package org.valkyrienskies.mod.common.ships.physics_data;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Stores data used by {@link org.valkyrienskies.mod.common.physics.PhysicsCalculations}
 */
public class ShipPhysicsData {
    @JsonSerialize(as = Vector3d.class)
    @JsonDeserialize(as = Vector3d.class)
    private Vector3dc linearVelocity;
    @JsonSerialize(as = Vector3d.class)
    @JsonDeserialize(as = Vector3d.class)
    private Vector3dc angularVelocity;

    public ShipPhysicsData() {
    }

    public ShipPhysicsData(Vector3dc linearVelocity, Vector3dc angularVelocity) {
        this.linearVelocity = linearVelocity;
        this.angularVelocity = angularVelocity;
    }

    public Vector3dc getLinearVelocity() {
        return linearVelocity;
    }

    public void setLinearVelocity(Vector3dc linearVelocity) {
        this.linearVelocity = linearVelocity;
    }

    public Vector3dc getAngularVelocity() {
        return angularVelocity;
    }

    public void setAngularVelocity(Vector3dc angularVelocity) {
        this.angularVelocity = angularVelocity;
    }
}
