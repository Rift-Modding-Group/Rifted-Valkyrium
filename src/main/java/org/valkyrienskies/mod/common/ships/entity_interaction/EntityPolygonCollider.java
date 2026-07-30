package org.valkyrienskies.mod.common.ships.entity_interaction;

import org.jetbrains.annotations.NotNull;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.util.TransformedAABB;

/**
 * Player-only swept AABB separator used by vanilla movement injection. Ship/world/entity physics
 * collision is handled by the active physics backend; this exists only because
 * players are intentionally not dynamic physics actors.
 */
public class EntityPolygonCollider {
    @NotNull
    private final Vector3dc[] collisionAxes;
    @NotNull
    private final EntityCollisionObject[] collisions;
    @NotNull
    private final TransformedAABB entity;
    @NotNull
    private final TransformedAABB block;
    @NotNull
    private final Vector3dc entityVelocity;
    private boolean separated = false;
    private int minDistanceIndex;
    private boolean originallySeparated;

    public EntityPolygonCollider(@NotNull TransformedAABB movable, @NotNull TransformedAABB stationary, @NotNull Vector3dc[] axes, @NotNull Vector3dc entityVel) {
        this.collisionAxes = axes;
        this.entity = movable;
        this.block = stationary;
        this.entityVelocity = entityVel;
        this.collisions = new EntityCollisionObject[this.collisionAxes.length];
        processData();
    }

    public void processData() {
        this.separated = false;
        for (int i = 0; i < collisions.length; i++) {
            if (!this.separated) {
                collisions[i] = new EntityCollisionObject(entity, block, collisionAxes[i], entityVelocity);
                if (collisions[i].arePolygonsSeperated()) {
                    this.separated = true;
                    break;
                }
                if (!this.collisions[i].werePolygonsInitiallyColliding()) {
                    this.originallySeparated = true;
                }
            }
        }
        if (!this.separated) {
            double minDistance = 420;
            for (int i = 0; i < this.collisions.length; i++) {
                if (this.originallySeparated) {
                    double normalizedDistance = Math.abs((collisions[i].getCollisionPenetrationDistance() - collisions[i].getVelDot()) / collisions[i].getVelDot());
                    if (normalizedDistance < minDistance && !collisions[i].werePolygonsInitiallyColliding()) {
                        this.minDistanceIndex = i;
                        minDistance = normalizedDistance;
                    }
                }
                else if (Math.abs(collisions[i].getCollisionPenetrationDistance()) < minDistance) {
                    this.minDistanceIndex = i;
                    minDistance = Math.abs(collisions[i].getCollisionPenetrationDistance());
                }
            }
        }
    }

    public boolean arePolygonsSeparated() {
        return this.separated;
    }

    public Vector3dc[] getCollisionAxes() {
        return this.collisionAxes;
    }

    public EntityCollisionObject[] getCollisions() {
        return this.collisions;
    }

    public int getMinDistanceIndex() {
        return this.minDistanceIndex;
    }
}
