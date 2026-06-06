package org.valkyrienskies.mod.common.physics.collision;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.mod.common.physics.PhysXCollisionFilters;
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
import java.util.List;

/**
 * Information involving the blockpos to collide with the ship is contained here.
 * */
public class PhysXBlockCollider extends AbstractPhysXCollisionObject {
    private final BlockPos pos;
    private final boolean liquid;
    private final PxRigidStatic actor;
    @NotNull
    private final PxMaterial material;
    private final int stateHash;

    public PhysXBlockCollider(@NotNull PxPhysics physics, @NotNull PxScene scene, World world, BlockPos pos, IBlockState state) {
        super(physics, scene);
        this.pos = pos.toImmutable();
        this.liquid = isLiquid(state);
        this.material = physics.createMaterial(0.8f, 0.8f, 0.02f);
        this.stateHash = state.hashCode();

        PxTransform actorTransform = createTransform(pos.getX(), pos.getY(), pos.getZ());
        this.actor = this.physics.createRigidStatic(actorTransform);
        actorTransform.destroy();

        for (AxisAlignedBB box : getCollisionBoxes(world, pos, state, this.liquid)) {
            this.attachBoxShape(box, this.liquid);
        }
        this.scene.addActor(this.actor);
    }

    public boolean matches(IBlockState state) {
        return this.stateHash == state.hashCode() && this.liquid == isLiquid(state);
    }

    public boolean isLiquid() {
        return this.liquid;
    }

    public BlockPos getPos() {
        return this.pos;
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
        return state.getBlock() instanceof BlockLiquid || material == Material.WATER || material == Material.LAVA;
    }

    public static boolean isCollidableWorldState(IBlockState state) {
        return !state.getMaterial().equals(Material.AIR) && (isLiquid(state) || state.getMaterial().blocksMovement());
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
        if (boxes.isEmpty() && (forceFullBlock || state.getMaterial().blocksMovement())) {
            AxisAlignedBB fallback = getFallbackCollisionBox(world, pos, state);
            if (fallback != null) boxes.add(fallback);
        }
        return boxes;
    }

    @Nullable
    private static AxisAlignedBB getFallbackCollisionBox(World world, BlockPos pos, IBlockState state) {
        try {
            AxisAlignedBB local = state.getCollisionBoundingBox(world, pos);
            if (local != null) return local.offset(pos);
        }
        //fall back to a full block below
        catch (Throwable ignored) {}

        return new AxisAlignedBB(pos);
    }
}
