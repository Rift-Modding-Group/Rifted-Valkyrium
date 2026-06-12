package org.valkyrienskies.mod.common.physics.collision;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.mod.common.physics.PhysXCollisionFilters;
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

/**
 * Information involving the blockpos to collide with the ship is contained here.
 * */
public class PhysXBlockCollider extends AbstractPhysXCollisionObject {
    public static final double ACTOR_SCAN_GROW = 3D;

    @NotNull
    private final World world;
    @NotNull
    private final BlockPos pos;
    @NotNull
    private final IBlockState state;
    private final boolean liquid;
    @NotNull
    private final PxRigidStatic actor;
    @NotNull
    private final PxMaterial material;
    private final int stateHash;

    public PhysXBlockCollider(
            @NotNull PxPhysics physics,
            @NotNull PxScene scene,
            @NotNull World world,
            @NotNull BlockPos pos,
            @NotNull IBlockState state
    ) {
        super(physics, scene);
        this.world = world;
        this.pos = pos.toImmutable();
        this.state = state;
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

    @NotNull
    public World getWorld() {
        return this.world;
    }

    @NotNull
    public BlockPos getPos() {
        return this.pos;
    }

    @NotNull
    public IBlockState getState() {
        return this.state;
    }

    @Override
    @NotNull
    public PxMaterial getMaterial() {
        return this.material;
    }

    @Override
    public boolean isStillValid(@NotNull World hostWorld, @NotNull Collection<PhysicsObject> shipsWithPhysics) {
        if (this.world != hostWorld) return false;

        IBlockState currentState = this.world.getBlockState(this.pos);
        if (!isCollidableWorldState(currentState) || !this.matches(currentState)) {
            return false;
        }

        for (PhysicsObject ship : shipsWithPhysics) {
            AxisAlignedBB shipAabb = ship.getPhysicsTransformAABB();
            if (shipAabb != null && this.isWithinShipScan(hostWorld, shipAabb)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void updateBeforeSimulation(
            @NotNull World hostWorld,
            @NotNull Collection<PhysicsObject> shipsWithPhysics,
            @NotNull List<AbstractPhysXCollisionObject> collisionObjects,
            double timeStep
    ) {}

    @Override
    public void updateAfterSimulation(
            @NotNull World hostWorld,
            @NotNull Collection<PhysicsObject> shipsWithPhysics,
            @NotNull List<AbstractPhysXCollisionObject> collisionObjects,
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

    private boolean isWithinShipScan(World hostWorld, AxisAlignedBB shipAabb) {
        AxisAlignedBB scan = shipAabb.grow(ACTOR_SCAN_GROW);
        int minX = (int) Math.floor(scan.minX);
        int minY = Math.max(0, (int) Math.floor(scan.minY));
        int minZ = (int) Math.floor(scan.minZ);
        int maxX = (int) Math.ceil(scan.maxX);
        int maxY = Math.min(hostWorld.getHeight() - 1, (int) Math.ceil(scan.maxY));
        int maxZ = (int) Math.ceil(scan.maxZ);
        return this.pos.getX() >= minX && this.pos.getX() <= maxX
                && this.pos.getY() >= minY && this.pos.getY() <= maxY
                && this.pos.getZ() >= minZ && this.pos.getZ() <= maxZ;
    }

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
}
