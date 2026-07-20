package org.valkyrienskies.mod.common.physics.physx.collision;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import java.util.Map;
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
     * Get identifier that distinguishes collision object
     * */
    @NotNull
    public abstract Identifier getIdentifier();

    public abstract void updateBeforeSimulation(
            @NotNull World hostWorld,
            @NotNull Collection<PhysicsObject> shipsWithPhysics,
            @NotNull Map<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject> collisionObjects,
            @NotNull List<PhysXBlockSectionCollider> blockSectionsWithLiquids,
            double timeStep
    );

    public abstract void updateAfterSimulation(
            @NotNull World hostWorld,
            @NotNull Collection<PhysicsObject> shipsWithPhysics,
            @NotNull Map<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject> collisionObjects,
            double timeStep
    );

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
    }

    protected void detachShape(PxShape shape) {
        if (shape != null) this.getActor().detachShape(shape, true);
    }

    protected boolean attachShape(PxShape shape) {
        boolean attached = this.getActor().attachShape(shape);
        shape.release();
        return attached;
    }

    /**
     * This is for creating a box shape from the objects normal AABB and its material
     * */
    @Nullable
    protected PxShape createBoxShape(@NotNull AxisAlignedBB box, @NotNull PxMaterial material) {
        PxBoxGeometry geometry = new PxBoxGeometry(
            (float) Math.max((box.maxX - box.minX) * 0.5D, 0.0001D),
            (float) Math.max((box.maxY - box.minY) * 0.5D, 0.0001D),
            (float) Math.max((box.maxZ - box.minZ) * 0.5D, 0.0001D)
        );
        PxShape shape = this.physics.createShape(geometry, material, true);
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

    /**
     * A special class for collision objects that defines how it is identified
     * */
    public static abstract class Identifier {
        @Override
        public abstract boolean equals(Object object);

        @Override
        public abstract int hashCode();
    }
}
