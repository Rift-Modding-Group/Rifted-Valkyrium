package org.valkyrienskies.mod.common.ships.physics_data;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.physics.PhysicsCalculations;

import java.util.HashSet;
import java.util.Set;

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
    private transient Set<BlockPos> dirtyCollisionShapePositions = new HashSet<>();

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

    public synchronized void markCollisionShapeDirty() {
        this.collisionShapeDirty = true;
        this.getDirtyCollisionShapePositions().clear();
    }

    public synchronized void markCollisionShapeDirty(BlockPos pos) {
        if (this.collisionShapeDirty) return;
        this.getDirtyCollisionShapePositions().add(pos.toImmutable());
    }

    public synchronized boolean consumeCollisionShapeDirty() {
        boolean result = this.collisionShapeDirty;
        this.collisionShapeDirty = false;
        if (result) this.getDirtyCollisionShapePositions().clear();
        return result;
    }

    public synchronized Set<BlockPos> consumeDirtyCollisionShapePositions() {
        Set<BlockPos> result = new HashSet<>(this.getDirtyCollisionShapePositions());
        this.getDirtyCollisionShapePositions().clear();
        return result;
    }

    private Set<BlockPos> getDirtyCollisionShapePositions() {
        if (this.dirtyCollisionShapePositions == null) {
            this.dirtyCollisionShapePositions = new HashSet<>();
        }
        return this.dirtyCollisionShapePositions;
    }
}
