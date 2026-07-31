package org.valkyrienskies.mod.common.physics.physx.bodies;

import net.minecraft.util.math.AxisAlignedBB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import physx.geometry.PxBoxGeometry;
import physx.physics.PxMaterial;
import physx.physics.PxPhysics;
import physx.physics.PxRigidActor;
import physx.physics.PxScene;
import physx.physics.PxShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Abstract class for handling collision of all physics participants in PhysX.
 */
public abstract class AbstractPhysXCollisionObject<I extends AbstractPhysXCollisionObject.Identifier> {
    //identifier that distinguishes physics object
    @NotNull
    protected final I identifier;

    //physics from the PhysX backend
    @NotNull
    protected final PxPhysics physics;

    //the scene the physics happens in
    @NotNull
    protected final PxScene scene;

    //actor on which the physics is acted upon
    @NotNull
    protected final PxRigidActor actor;

    //shapes owned by this collision object's actor
    @NotNull
    private final List<PxShape> shapes = new ArrayList<>();

    //flag for determining if this was ever released from memory and PhysX
    protected boolean released;

    protected AbstractPhysXCollisionObject(
            @NotNull I identifier,
            @NotNull PxPhysics physics,
            @NotNull PxScene scene,
            @NotNull Supplier<? extends PxRigidActor> actorFactory
    ) {
        this.identifier = Objects.requireNonNull(identifier, "identifier");
        this.physics = Objects.requireNonNull(physics, "physics");
        this.scene = Objects.requireNonNull(scene, "scene");
        this.actor = Objects.requireNonNull(Objects.requireNonNull(actorFactory, "actorFactory").get(), "actorFactory returned null");
        this.scene.addActor(this.actor);
        this.released = false;
    }

    //---shape related functions---
    /**
     * Attach a newly-created shape and transfer its ownership to this collision object.
     */
    protected boolean addShape(@NotNull PxShape shape) {
        boolean attached = this.actor.attachShape(shape);
        shape.release();
        if (attached) this.shapes.add(shape);
        return attached;
    }

    /**
     * Detach and release a shape owned by this collision object.
     */
    protected void releaseShape(@Nullable PxShape shape) {
        if (shape == null || !this.shapes.remove(shape)) return;
        this.actor.detachShape(shape, true);
    }

    /**
     * Detach and release every shape owned by this collision object.
     */
    protected void clearShapes() {
        for (PxShape shape : this.shapes) this.actor.detachShape(shape, true);
        this.shapes.clear();
    }

    protected boolean hasShapes() {
        return !this.shapes.isEmpty();
    }

    protected int getShapeCount() {
        return this.shapes.size();
    }

    /**
     * This is for creating a box shape from the objects normal AABB and its material.
     * Well to be frank almost everything in minecraft is a box so... uhh.. xd?
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

    //---other helper functions---
    /**
     * Get identifier that distinguishes collision object
     * */
    @NotNull
    public I getIdentifier() {
        return this.identifier;
    }

    /**
     * For releasing this collision object from memory.
     * */
    public void release() {
        if (this.released) return;
        this.clearShapes();
        this.scene.removeActor(this.actor, true);
        this.actor.release();
        this.released = true;
    }

    /**
     * A special class for collision objects that defines how it is identified in the physics backend
     * */
    public static abstract class Identifier {
        @Override
        public abstract boolean equals(Object object);

        @Override
        public abstract int hashCode();
    }
}
