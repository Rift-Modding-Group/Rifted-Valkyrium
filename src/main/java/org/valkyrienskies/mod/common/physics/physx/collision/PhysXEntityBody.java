package org.valkyrienskies.mod.common.physics.physx.collision;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.mod.common.physics.physx.PhysXCollisionFilters;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import physx.common.PxTransform;
import physx.physics.PxMaterial;
import physx.physics.PxPhysics;
import physx.physics.PxRigidActor;
import physx.physics.PxRigidBodyFlagEnum;
import physx.physics.PxRigidDynamic;
import physx.physics.PxScene;
import physx.physics.PxShape;

import java.util.Collection;
import java.util.Map;

/**
 * Information involving the entity to collide with the ship is contained here.
 * */
public class PhysXEntityBody extends AbstractPhysXCollisionObject {
    @NotNull
    private final PxRigidDynamic actor;
    @NotNull
    private final PxMaterial material;
    @NotNull
    private final Entity entity;
    @NotNull
    private final Identifier identifier;

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
        this.entity = entity;
        this.identifier = new Identifier(entity);
    }

    @Override
    @NotNull
    public Identifier getIdentifier() {
        return this.identifier;
    }

    @Override
    public void updateBeforeSimulation(
            @NotNull World hostWorld,
            @NotNull Collection<PhysicsObject> shipsWithPhysics,
            @NotNull Map<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject> collisionObjects,
            double timeStep
    ) {
        AxisAlignedBB bb = this.entity.getEntityBoundingBox();
        this.rebuildShape(bb);
        PxTransform target = createTransform(bb);
        this.actor.setKinematicTarget(target);
        target.destroy();
    }

    @Override
    public void updateAfterSimulation(
            @NotNull World hostWorld,
            @NotNull Collection<PhysicsObject> shipsWithPhysics,
            @NotNull Map<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject> collisionObjects,
            double timeStep
    ) {}

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
        if (!this.attachShape(this.shape)) this.shape = null;
    }

    public static final class Identifier extends AbstractPhysXCollisionObject.Identifier {
        @NotNull
        private final World world;
        @NotNull
        private final Entity entity;

        public Identifier(@NotNull Entity entity) {
            this.world = entity.world;
            this.entity = entity;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof Identifier that)) return false;
            return this.world == that.world && this.entity == that.entity;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(this.world) + System.identityHashCode(this.entity);
        }
    }
}
