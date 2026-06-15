package org.valkyrienskies.mod.common.physics.physx.collision;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.mod.common.physics.physx.PhysXActorUtil;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import physx.common.PxQuat;
import physx.common.PxTransform;
import physx.common.PxVec3;
import physx.geometry.PxBoxGeometry;
import physx.physics.PxMaterial;
import physx.physics.PxPhysics;
import physx.physics.PxRigidActor;
import physx.physics.PxScene;
import physx.physics.PxShape;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Abstract class for handling collision of all physics participants in PhysX.
 */
public abstract class AbstractPhysXCollisionObject {
    @NotNull
    protected final PxPhysics physics;
    @NotNull
    protected final PxScene scene;
    private boolean released;

    protected AbstractPhysXCollisionObject(@NotNull PxPhysics physics, @NotNull PxScene scene) {
        this.physics = Objects.requireNonNull(physics, "physics");
        this.scene = Objects.requireNonNull(scene, "scene");
        this.released = false;
    }

    /**
     * Get PhysX material.
     * */
    @NotNull
    public abstract PxMaterial getMaterial();

    /**
     * Test if the collision object is still valid. False means it gets removed.
     * */
    public abstract boolean isStillValid(@NotNull World hostWorld, @NotNull Collection<PhysicsObject> shipsWithPhysics);

    public abstract void updateBeforeSimulation(
            @NotNull World hostWorld,
            @NotNull Collection<PhysicsObject> shipsWithPhysics,
            @NotNull List<AbstractPhysXCollisionObject> collisionObjects,
            double timeStep
    );

    public abstract void updateAfterSimulation(
            @NotNull World hostWorld,
            @NotNull Collection<PhysicsObject> shipsWithPhysics,
            @NotNull List<AbstractPhysXCollisionObject> collisionObjects,
            double timeStep
    );

    public boolean isLiquidBlockIntersecting(@NotNull AxisAlignedBB box) {
        return false;
    }

    @NotNull
    protected abstract PxRigidActor getActor();

    protected abstract void releaseShapes();

    /**
     * For releasing this collision object from memory.
     * */
    public void release() {
        if (this.released) return;
        this.released = true;
        this.releaseShapes();
        this.scene.removeActor(this.getActor(), true);
        this.getActor().release();
        this.getMaterial().release();
    }

    protected void detachShape(PxShape shape) {
        if (shape != null) this.getActor().detachShape(shape, true);
    }

    protected boolean attachShape(PxShape shape) {
        boolean attached = this.getActor().attachShape(shape);
        shape.release();
        return attached;
    }

    protected PxShape createBoxShape(AxisAlignedBB box) {
        return this.createBoxShape(
            (box.maxX - box.minX) * 0.5D,
            (box.maxY - box.minY) * 0.5D,
            (box.maxZ - box.minZ) * 0.5D
        );
    }

    protected PxShape createBoxShape(double halfX, double halfY, double halfZ) {
        PxBoxGeometry geometry = new PxBoxGeometry(
            (float) Math.max(halfX, 0.0001D),
            (float) Math.max(halfY, 0.0001D),
            (float) Math.max(halfZ, 0.0001D)
        );
        PxShape shape = this.physics.createShape(geometry, this.getMaterial(), true);
        geometry.destroy();
        return shape;
    }

    protected PxTransform createTransform(double x, double y, double z) {
        PxVec3 position = PhysXActorUtil.toPxVec(x, y, z);
        PxQuat rotation = new PxQuat(0, 0, 0, 1);
        PxTransform transform = new PxTransform(position, rotation);
        position.destroy();
        rotation.destroy();
        return transform;
    }

    protected PxTransform createTransform(AxisAlignedBB bb) {
        return this.createTransform(
            (bb.minX + bb.maxX) * 0.5D,
            (bb.minY + bb.maxY) * 0.5D,
            (bb.minZ + bb.maxZ) * 0.5D
        );
    }
}
