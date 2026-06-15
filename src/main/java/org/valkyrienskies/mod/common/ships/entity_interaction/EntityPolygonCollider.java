package org.valkyrienskies.mod.common.ships.entity_interaction;

import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.util.TransformedAABB;

/**
 * Player-only swept AABB separator used by vanilla movement injection. Ship/world/entity physics
 * collision is handled by PhysX; this exists only because players are intentionally not dynamic
 * PhysX actors.
 */
class EntityPolygonCollider {

    private final Vector3dc[] collisionAxes;
    private final EntityCollisionObject[] collisions;
    private final TransformedAABB entity;
    private final TransformedAABB block;
    private final Vector3dc entityVelocity;
    private boolean separated = false;
    private int minDistanceIndex;
    private boolean originallySeparated;

    EntityPolygonCollider(TransformedAABB movable, TransformedAABB stationary, Vector3dc[] axes, Vector3dc entityVel) {
        collisionAxes = axes;
        entity = movable;
        block = stationary;
        entityVelocity = entityVel;
        collisions = new EntityCollisionObject[collisionAxes.length];
        processData();
    }

    void processData() {
        separated = false;
        for (int i = 0; i < collisions.length; i++) {
            if (!separated) {
                collisions[i] = new EntityCollisionObject(entity, block, collisionAxes[i], entityVelocity);
                if (collisions[i].arePolygonsSeperated()) {
                    separated = true;
                    break;
                }
                if (!collisions[i].werePolygonsInitiallyColliding()) {
                    originallySeparated = true;
                }
            }
        }
        if (!separated) {
            double minDistance = 420;
            for (int i = 0; i < collisions.length; i++) {
                if (originallySeparated) {
                    double normalizedDistance = Math.abs((collisions[i].getCollisionPenetrationDistance() - collisions[i].getVelDot()) / collisions[i].getVelDot());
                    if (normalizedDistance < minDistance && !collisions[i].werePolygonsInitiallyColliding()) {
                        minDistanceIndex = i;
                        minDistance = normalizedDistance;
                    }
                } else if (Math.abs(collisions[i].getCollisionPenetrationDistance()) < minDistance) {
                    minDistanceIndex = i;
                    minDistance = Math.abs(collisions[i].getCollisionPenetrationDistance());
                }
            }
        }
    }

    boolean arePolygonsSeparated() {
        return separated;
    }

    Vector3dc[] getCollisionAxes() {
        return collisionAxes;
    }

    EntityCollisionObject[] getCollisions() {
        return collisions;
    }

    int getMinDistanceIndex() {
        return minDistanceIndex;
    }
}
