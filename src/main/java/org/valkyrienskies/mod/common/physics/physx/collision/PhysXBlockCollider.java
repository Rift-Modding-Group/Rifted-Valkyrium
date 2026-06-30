package org.valkyrienskies.mod.common.physics.physx.collision;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.MaterialLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.mod.common.physics.physx.PhysXCollisionFilters;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import physx.common.PxTransform;
import physx.physics.PxMaterial;
import physx.physics.PxPhysics;
import physx.physics.PxRigidActor;
import physx.physics.PxRigidStatic;
import physx.physics.PxScene;
import physx.physics.PxShape;
import physx.physics.PxShapeFlagEnum;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Information involving the blockpos to collide with the ship is contained here.
 * */
public class PhysXBlockCollider extends AbstractPhysXCollisionObject {
    @NotNull
    private final BlockPos pos;
    @NotNull
    private final Identifier identifier;
    private final boolean liquid;
    @NotNull
    private final PxRigidStatic actor;
    @NotNull
    private final PxMaterial material;

    public PhysXBlockCollider(
            @NotNull PxPhysics physics,
            @NotNull PxScene scene,
            @NotNull World world,
            @NotNull BlockPos pos,
            @NotNull IBlockState state
    ) {
        super(physics, scene);
        this.pos = pos.toImmutable();
        this.identifier = new Identifier(world, pos, state);
        this.liquid = isLiquid(state);
        this.material = physics.createMaterial(0.8f, 0.8f, 0.02f);

        PxTransform actorTransform = createTransform(pos.getX(), pos.getY(), pos.getZ());
        this.actor = this.physics.createRigidStatic(actorTransform);
        actorTransform.destroy();

        for (AxisAlignedBB box : getCollisionBoxes(world, pos, state, this.liquid)) {
            this.attachBoxShape(box, this.liquid);
        }
        this.scene.addActor(this.actor);
    }

    @Override
    @NotNull
    public Identifier getIdentifier() {
        return this.identifier;
    }

    @Override
    @NotNull
    public PxMaterial getMaterial() {
        return this.material;
    }

    @Override
    public void updateBeforeSimulation(
            @NotNull World hostWorld,
            @NotNull Collection<PhysicsObject> shipsWithPhysics,
            @NotNull Map<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject> collisionObjects,
            double timeStep
    ) {}

    @Override
    public void updateAfterSimulation(
            @NotNull World hostWorld,
            @NotNull Collection<PhysicsObject> shipsWithPhysics,
            @NotNull Map<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject> collisionObjects,
            double timeStep
    ) {}

    @Override
    public boolean isLiquidBlockIntersecting(@NotNull AxisAlignedBB box) {
        return this.liquid && box.intersects(new AxisAlignedBB(this.pos));
    }

    @Override
    @NotNull
    protected PxRigidActor getActor() {
        return this.actor;
    }

    @Override //no shapes to release down here xd
    protected void releaseShapes() {}

    private void attachBoxShape(AxisAlignedBB worldBox, boolean trigger) {
        PxShape shape = this.createBoxShape(worldBox);

        if (trigger) {
            shape.setFlag(PxShapeFlagEnum.eSIMULATION_SHAPE, false);
            shape.setFlag(PxShapeFlagEnum.eTRIGGER_SHAPE, true);
            PhysXCollisionFilters.CollisionGroup.LIQUID.setFilter(shape);
        }
        else PhysXCollisionFilters.CollisionGroup.WORLD.setFilter(shape);

        double centerX = (worldBox.minX + worldBox.maxX) * 0.5D - this.pos.getX();
        double centerY = (worldBox.minY + worldBox.maxY) * 0.5D - this.pos.getY();
        double centerZ = (worldBox.minZ + worldBox.maxZ) * 0.5D - this.pos.getZ();
        PxTransform localPose = createTransform(centerX, centerY, centerZ);
        shape.setLocalPose(localPose);
        localPose.destroy();

        this.attachShape(shape);
    }

    //-----static helper functions for use in PhysXWorldBackEnd-----
    public static boolean isLiquid(IBlockState state) {
        Material material = state.getMaterial();
        return state.getBlock() instanceof BlockLiquid || material instanceof MaterialLiquid;
    }

    public static List<AxisAlignedBB> getCollisionBoxes(World world, BlockPos pos, IBlockState state, boolean forceFullBlock) {
        List<AxisAlignedBB> boxes = new ArrayList<>();
        if (!forceFullBlock) {
            try {
                state.addCollisionBoxToList(world, pos, new AxisAlignedBB(pos), boxes, null, false);
            }
            catch (Throwable ignored) {
                boxes.clear();
            }
        }
        if (boxes.isEmpty()) {
            AxisAlignedBB fallback = getFallbackCollisionBox(world, pos, state, forceFullBlock);
            if (fallback != null) boxes.add(fallback);
        }
        return boxes;
    }

    @Nullable
    private static AxisAlignedBB getFallbackCollisionBox(World world, BlockPos pos, IBlockState state, boolean forceFullBlock) {
        if (forceFullBlock) return new AxisAlignedBB(pos);

        try {
            AxisAlignedBB local = state.getCollisionBoundingBox(world, pos);
            if (local != null) return local.offset(pos);
        }
        //fall back to a full block below
        catch (Throwable ignored) {
            if (state.getMaterial().blocksMovement()) return new AxisAlignedBB(pos);
        }

        return null;
    }

    public static final class Identifier extends AbstractPhysXCollisionObject.Identifier {
        @NotNull
        private final World world;
        @NotNull
        private final BlockPos pos;
        private final int stateHash;
        private final boolean liquid;

        public Identifier(@NotNull World world, @NotNull BlockPos pos, @NotNull IBlockState state) {
            this.world = world;
            this.pos = pos.toImmutable();
            this.stateHash = state.hashCode();
            this.liquid = isLiquid(state);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof Identifier that)) return false;
            return this.world == that.world
                    && this.stateHash == that.stateHash
                    && this.liquid == that.liquid
                    && this.pos.equals(that.pos);
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(this.world);
            result = 31 * result + this.pos.hashCode();
            result = 31 * result + this.stateHash;
            result = 31 * result + Boolean.hashCode(this.liquid);
            return result;
        }
    }
}
