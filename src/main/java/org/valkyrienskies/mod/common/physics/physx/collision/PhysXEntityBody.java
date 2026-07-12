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
import java.util.List;
import java.util.Map;

/**
 * Information involving the entity to collide with the ship is contained here.
 * */
public class PhysXEntityBody extends AbstractPhysXCollisionObject {
    private static final double ENTITY_SHAPE_SIZE_EPSILON = 1.0E-6D;

    @NotNull
    private final PxRigidDynamic actor;
    @NotNull
    private final PxMaterial material;
    @NotNull
    private final Entity entity;
    @NotNull
    private final Identifier identifier;

    private PxShape shape;
    private double shapeSizeX;
    private double shapeSizeY;
    private double shapeSizeZ;
    private boolean hasCachedShapeSize;

    public PhysXEntityBody(
            @NotNull PxPhysics physics,
            @NotNull PxScene scene,
            @NotNull PxMaterial material,
            @NotNull Entity entity
    ) {
        super(physics, scene);
        this.material = material;
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
            List<AbstractPhysXCollisionObject> liquidCollisionObjects,
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
            this.clearCachedShapeSize();
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

    /**
     * Rebuild shape based on given AABB, said AABB is the
     * main collision box of the entity
     * */
    private void rebuildShape(AxisAlignedBB bb) {
        double sizeX = bb.maxX - bb.minX;
        double sizeY = bb.maxY - bb.minY;
        double sizeZ = bb.maxZ - bb.minZ;

        if (this.shape == null || !this.hasCachedShapeSize || !this.hasSameShapeSize(sizeX, sizeY, sizeZ)) {
            //remove already existing shape if it does
            if (this.shape != null) {
                this.detachShape(this.shape);
                this.shape = null;
                this.clearCachedShapeSize();
            }

            //recreate shape
            this.shape = this.createBoxShape(bb);
            PhysXCollisionFilters.CollisionGroup.ENTITY.setFilter(this.shape);
            if (this.attachShape(this.shape)) {
                this.shapeSizeX = sizeX;
                this.shapeSizeY = sizeY;
                this.shapeSizeZ = sizeZ;
                this.hasCachedShapeSize = true;
            }
            else {
                this.shape = null;
                this.clearCachedShapeSize();
            }
        }
    }

    private boolean hasSameShapeSize(double sizeX, double sizeY, double sizeZ) {
        return Math.abs(this.shapeSizeX - sizeX) <= ENTITY_SHAPE_SIZE_EPSILON
                && Math.abs(this.shapeSizeY - sizeY) <= ENTITY_SHAPE_SIZE_EPSILON
                && Math.abs(this.shapeSizeZ - sizeZ) <= ENTITY_SHAPE_SIZE_EPSILON;
    }

    private void clearCachedShapeSize() {
        this.shapeSizeX = 0D;
        this.shapeSizeY = 0D;
        this.shapeSizeZ = 0D;
        this.hasCachedShapeSize = false;
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
