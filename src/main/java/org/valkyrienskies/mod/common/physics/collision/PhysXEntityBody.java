package org.valkyrienskies.mod.common.physics.collision;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.mod.common.physics.PhysXCollisionFilters;
import physx.common.PxTransform;
import physx.physics.PxMaterial;
import physx.physics.PxPhysics;
import physx.physics.PxRigidActor;
import physx.physics.PxRigidBodyFlagEnum;
import physx.physics.PxRigidDynamic;
import physx.physics.PxScene;
import physx.physics.PxShape;

/**
 * Information involving the entity to collide with the ship is contained here.
 * Entity isn't held here however, only its AABB is considered.
 * */
public class PhysXEntityBody extends AbstractPhysXCollisionObject {
    private final PxRigidDynamic actor;
    @NotNull
    private final PxMaterial material;
    private PxShape shape;

    public PhysXEntityBody(@NotNull PxPhysics physics, @NotNull PxScene scene, @NotNull Entity entity) {
        super(physics, scene);
        this.material = physics.createMaterial(0.4f, 0.4f, 0.0f);
        PxTransform transform = createTransform(entity.getEntityBoundingBox());
        this.actor = this.physics.createRigidDynamic(transform);
        transform.destroy();
        this.actor.setRigidBodyFlag(PxRigidBodyFlagEnum.eKINEMATIC, true);
        this.rebuildShape(entity.getEntityBoundingBox());
        this.scene.addActor(this.actor);
    }

    public void sync(Entity entity) {
        AxisAlignedBB bb = entity.getEntityBoundingBox();
        this.rebuildShape(bb);
        PxTransform target = createTransform(bb);
        this.actor.setKinematicTarget(target);
        target.destroy();
    }

    @Override
    protected void releaseShapes() {
        if (this.shape != null) {
            this.detachShape(this.shape);
            this.shape = null;
        }
    }

    @Override
    @NotNull
    public PxMaterial getMaterial() {
        return this.material;
    }

    @Override
    @NotNull
    protected PxRigidActor getActor() {
        return this.actor;
    }

    private void rebuildShape(AxisAlignedBB bb) {
        if (this.shape != null) {
            this.detachShape(this.shape);
            this.shape = null;
        }
        this.shape = this.createBoxShape(bb);
        PhysXCollisionFilters.CollisionGroup.ENTITY.setFilter(this.shape);
        if (!this.attachShape(this.shape)) {
            this.shape = null;
        }
    }
}
