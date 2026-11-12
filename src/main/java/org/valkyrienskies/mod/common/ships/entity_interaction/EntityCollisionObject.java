package org.valkyrienskies.mod.common.ships.entity_interaction;

import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.util.TransformedAABB;

/**
 * An enhanced version of the collision Object, designed to prevent entities from moving through a
 * polygon
 *
 * @author thebest108
 */
public class EntityCollisionObject {

    private final Vector3dc axis;
    private final TransformedAABB movable, fixed;
    private double penetrationDistance;
    private boolean separated;
    private final Vector3dc entityVelocity;
    private boolean originallyCollided;
    private double velDot;

    public EntityCollisionObject(TransformedAABB movable_, TransformedAABB stationary, Vector3dc axes, Vector3dc entityVel) {
        axis = axes;
        movable = movable_;
        fixed = stationary;
        entityVelocity = entityVel;
        originallyCollided = false;
        generateCollision();
    }

    public void generateCollision() {
        velDot = -entityVelocity.dot(axis);
        // playerMinMax =
        // BigBastardMath.getMinMaxOfArray(movable.getProjectionOnVector(axis));
        // blockMinMax =
        // BigBastardMath.getMinMaxOfArray(fixed.getProjectionOnVector(axis));
        // double movMaxFixMin = playerMinMax[0]-blockMinMax[1];
        // double movMinFixMax = playerMinMax[1]-blockMinMax[0];

        // NOTE: This code isnt compatible or readable, but its faster
        double dot = axis.dot(movable.getVertices()[0]);
        double playerMin = dot, playerMax = dot;
        for (int i = 1; i < movable.getVertices().length; i++) {
            dot = axis.dot(movable.getVertices()[i]);
            if (dot < playerMin) {
                playerMin = dot;
            }
            if (dot > playerMax) {
                playerMax = dot;
            }
        }

        dot = axis.dot(fixed.getVertices()[0]);
        double blockMin = dot, blockMax = dot;
        for (int i = 1; i < fixed.getVertices().length; i++) {
            dot = axis.dot(fixed.getVertices()[i]);
            if (dot < blockMin) {
                blockMin = dot;
            }
            if (dot > blockMax) {
                blockMax = dot;
            }
        }

        double movMaxFixMin = playerMin - blockMax;
        double movMinFixMax = playerMax - blockMin;

        boolean useDefault = true;
        // Treat an approaching surface contact as a swept collision, including transform rounding.
        boolean approachingContact = movMaxFixMin >= -1.0E-7D && this.velDot > 0D || movMinFixMax <= 1.0E-7D && this.velDot < 0D;
        //original position not colliding, use velocity based bastards
        if (movMaxFixMin > 0 || movMinFixMax < 0 || approachingContact) useDefault = false;
        else this.originallyCollided = true;

        if (this.velDot > 0) movMaxFixMin -= this.velDot;
        else movMinFixMax -= this.velDot;

        if (movMaxFixMin > 0 || movMinFixMax < 0) {
            this.separated = true;
            this.penetrationDistance = 0.0D;
            return;
        }
        // Set the penetration to be the smaller distance
        if (useDefault || this.velDot == 0D) {
            if (Math.abs(movMaxFixMin) < Math.abs(movMinFixMax)) {
                this.penetrationDistance = movMaxFixMin;
            }
            else {
                this.penetrationDistance = movMinFixMax;
            }
        }
        else {
            if (Math.signum(this.velDot) != Math.signum(movMinFixMax)) {
                this.penetrationDistance = movMinFixMax;
            }
            else {
                this.penetrationDistance = movMaxFixMin;
            }
        }
        separated = false;
    }

    public Vector3d getResponse() {
        return axis.mul(-penetrationDistance, new Vector3d());
    }

    public Vector3dc getCollisionNormal() {
        return axis;
    }

    public double getCollisionPenetrationDistance() {
        return penetrationDistance;
    }

    public boolean arePolygonsSeperated() {
        return separated;
    }

    public boolean werePolygonsInitiallyColliding() {
        return originallyCollided;
    }

    public double getVelDot() {
        return velDot;
    }
}
