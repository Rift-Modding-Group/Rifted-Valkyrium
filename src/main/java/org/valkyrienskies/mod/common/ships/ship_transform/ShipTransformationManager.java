package org.valkyrienskies.mod.common.ships.ship_transform;

import net.minecraft.util.math.AxisAlignedBB;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.util.TransformedAABB;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import valkyrienwarfare.api.TransformType;

/**
 * Stores various coordinates and transforms for the ship.
 */
public class ShipTransformationManager {
    private final PhysicsObject parent;
    public Vector3dc[] normals;
    private ShipTransform currentTickTransform;
    private ShipTransform renderTransform;
    private ShipTransform prevTickTransform;
    // Used exclusively by the physics engine; should never even be used by the client.
    private ShipTransform currentPhysicsTransform;
    private ShipTransform prevPhysicsTransform;

    public ShipTransformationManager(PhysicsObject parent, ShipTransform initialTransform) {
        this.parent = parent;
        this.currentTickTransform = initialTransform;
        this.renderTransform = initialTransform;
        this.prevTickTransform = initialTransform;
        this.currentPhysicsTransform = initialTransform;
        this.prevPhysicsTransform = initialTransform;
        // Create the normals.
        this.normals = createCollisionNormals(initialTransform);
    }

    private static Vector3dc[] createCollisionNormals(ShipTransform transform) {
        // We edit a local array instead of normals to avoid data races.
        final Vector3dc[] newNormals = new Vector3dc[15];
        // Used to generate Normals for the Axis Aligned World
        final Vector3dc[] alignedNorms = TransformedAABB.generateAxisAlignedNorms();
        final Vector3dc[] rotatedNorms = generateRotationNormals(transform);
        for (int i = 0; i < 6; i++) {
            Vector3dc currentNorm;
            if (i < 3) currentNorm = alignedNorms[i];
            else currentNorm = rotatedNorms[i - 3];
            newNormals[i] = currentNorm;
        }
        int cont = 6;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                Vector3d norm = newNormals[i].cross(newNormals[j + 3], new Vector3d());
                norm.normalize();
                newNormals[cont] = norm;
                cont++;
            }
        }
        for (int i = 0; i < newNormals.length; i++) {
            if (newNormals[i].lengthSquared() < .01) {
                newNormals[i] = new Vector3d(0.0D, 1.0D, 0.0D);
            }
        }
        newNormals[0] = new Vector3d(1, 0, 0);
        newNormals[1] = new Vector3d(0, 1, 0);
        newNormals[2] = new Vector3d(0, 0, 1);

        return newNormals;
    }

    private static Vector3dc[] generateRotationNormals(ShipTransform shipTransform) {
        Vector3d[] norms = TransformedAABB.generateAxisAlignedNorms();
        for (int i = 0; i < 3; i++) {
            shipTransform.transformDirection(norms[i], TransformType.SUBSPACE_TO_GLOBAL);
        }
        return norms;
    }

    /**
     * Updates all the transformations, only updates the AABB if passed true.
     * @deprecated Use any one of new separate methods instead
     */
    @Deprecated
    public void updateAllTransforms(ShipTransform newTransform, boolean updatePhysicsTransform, boolean updateShipBoundingBox) {
        this.updateShipTransform(newTransform);
        if (updatePhysicsTransform) this.updatePhysicsTransform();
        if (updateShipBoundingBox) this.updateShipBoundingBox();
    }

    public void updateShipTransform(ShipTransform newTransform) {
        this.prevTickTransform = this.currentTickTransform;
        this.currentTickTransform = newTransform;
        if (this.prevTickTransform == null) this.prevTickTransform = this.currentTickTransform;
        this.normals = createCollisionNormals(currentTickTransform);
    }

    /**
     * Update physics transformation of the ship
     * This should only be called once when the ship finally loads from nbt
     * */
    public void updatePhysicsTransform() {
        this.parent.getPhysicsCalculations().generatePhysicsTransform();
        this.prevPhysicsTransform = this.currentPhysicsTransform;
    }

    /**
     * @deprecated Use {@link updateShipBoundingBox()} instead
     * */
    @Deprecated
    public void updateParentAABB() {
        this.updateShipBoundingBox();
    }

    /**
     * Update the ship's AABB
     * */
    public void updateShipBoundingBox() {
        // The client should never be updating the AABB on its own.
        if (this.parent.getWorld().isRemote) return;

        AxisAlignedBB subspaceBB = this.parent.getBlockPositions().makeAABB();
        if (subspaceBB == null) return; // The aabbMaker didn't know what the aabb was, just don't update the aabb for now.

        // Expand subspaceBB by 1 to fit the block grid.
        subspaceBB = subspaceBB.expand(1, 1, 1);
        // Now transform the subspaceBB to world coordinates
        TransformedAABB largerPoly = new TransformedAABB(subspaceBB, getCurrentTickTransform(), TransformType.SUBSPACE_TO_GLOBAL);
        // Set the ship AABB to that of the polygon.
        AxisAlignedBB worldBB = largerPoly.getEnclosedAABB();
        this.parent.setShipBoundingBox(worldBB);
    }

    /**
     * @return the current transformation being used this tick.
     */
    public ShipTransform getCurrentTickTransform() {
        return this.currentTickTransform;
    }

    /**
     * @return the renderTransform
     */
    public ShipTransform getRenderTransform() {
        if (!this.parent.getWorld().isRemote || this.renderTransform == null) {
            return this.currentTickTransform;
        }
        return this.renderTransform;
    }

    /**
     * @return the prevTransform
     */
    public ShipTransform getPrevTickTransform() {
        return prevTickTransform;
    }

    /**
     * Returns the transformation data used for physics processing.
     *
     * @return the physics transform
     */
    public ShipTransform getCurrentPhysicsTransform() {
        return currentPhysicsTransform;
    }

    public ShipTransform getPrevPhysicsTransform() {
        return prevPhysicsTransform;
    }

    public void updatePreviousPhysicsTransform() {
        this.prevPhysicsTransform = currentPhysicsTransform;
    }

    public void updateRenderTransform(double partialTick) {
        if (partialTick == 0) {
            renderTransform = prevTickTransform;
            return;
        } else if (partialTick == 1) {
            renderTransform = currentTickTransform;
            return;
        }
        ShipTransform prev = prevTickTransform;
        ShipTransform cur = currentTickTransform;
        Vector3dc shipCenter = parent.getCenterCoord();

        Vector3d prevPos = new Vector3d(shipCenter);
        Vector3d curPos = new Vector3d(shipCenter);
        prev.transformPosition(prevPos, TransformType.SUBSPACE_TO_GLOBAL);
        cur.transformPosition(curPos, TransformType.SUBSPACE_TO_GLOBAL);
        Vector3d deltaPos = curPos.sub(prevPos, new Vector3d());
        deltaPos.mul(partialTick);

        Vector3d partialPos = new Vector3d(prevPos);
        partialPos.add(deltaPos); // Now partialPos is complete

        Quaterniondc prevRot = prev.rotationQuaternion(TransformType.SUBSPACE_TO_GLOBAL);
        Quaterniondc curRot = cur.rotationQuaternion(TransformType.SUBSPACE_TO_GLOBAL);
        Quaterniondc partialRot = prevRot.slerp(curRot, partialTick, new Quaterniond()).normalize();

        // Put it all together to get the render transform.
        renderTransform = new ShipTransform(partialPos.x, partialPos.y,
            partialPos.z, partialRot, parent.getCenterCoord());
    }

    public void setCurrentTickTransform(ShipTransform currentTickTransform) {
        this.currentTickTransform = currentTickTransform;
    }

    public void setPrevTickTransform(ShipTransform prevTickTransform) {
        this.prevTickTransform = prevTickTransform;
    }

    public void setCurrentPhysicsTransform(ShipTransform currentPhysicsTransform) {
        this.currentPhysicsTransform = currentPhysicsTransform;
    }

    public void setPrevPhysicsTransform(ShipTransform prevPhysicsTransform) {
        this.prevPhysicsTransform = prevPhysicsTransform;
    }

}
