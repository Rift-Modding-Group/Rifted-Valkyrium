package org.valkyrienskies.mod.common.physics.physx.bodies;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.mod.common.physics.BlockSection;
import org.valkyrienskies.mod.common.physics.GreedyBlockMerger;
import org.valkyrienskies.mod.common.physics.physx.PhysXActor;
import org.valkyrienskies.mod.common.physics.physx.PhysXActorUtil;
import physx.common.PxTransform;
import physx.physics.PxMaterial;
import physx.physics.PxPhysics;
import physx.physics.PxRigidStatic;
import physx.physics.PxScene;
import physx.physics.PxShape;
import physx.physics.PxShapeFlagEnum;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * For block sections, which are just a group of blocks.
 */
public class PhysXBlockSectionBody extends AbstractPhysXCollisionObject<PhysXBlockSectionBody.Identifier, BlockSection> {
    @NotNull
    private final PxMaterial blockMaterial;
    @NotNull
    private final PxMaterial liquidMaterial;
    @NotNull
    private final List<AxisAlignedBB> liquidBoxes = new ArrayList<>();;

    public PhysXBlockSectionBody(
            @NotNull PhysXBlockSectionBody.Identifier identifier, @NotNull PxPhysics physics, @NotNull PxScene scene,
            @NotNull World world, @NotNull PxMaterial blockMaterial, @NotNull PxMaterial liquidMaterial,
            @NotNull List<BlockSection.BlockData> blocks
    ) {
        super(identifier, physics, scene, () -> {
            //anchor the static actor at this section's block-aligned origin.
            PxTransform actorTransform = PhysXActorUtil.createTransform(identifier.getOriginX(), identifier.getOriginY(), identifier.getOriginZ());
            PxRigidStatic toReturn = physics.createRigidStatic(actorTransform);
            actorTransform.destroy();
            return toReturn;
        });
        this.blockMaterial = blockMaterial;
        this.liquidMaterial = liquidMaterial;

        //split full cubes for GreedyBlockMerger so adjacent blocks become fewer PhysX shapes.
        GreedyBlockMerger mergeableSolidBlocks = new GreedyBlockMerger();
        List<BlockSection.BlockData> separateBlocks = new ArrayList<>();
        for (BlockSection.BlockData block : blocks) {
            if (block.liquid()) {
                this.liquidBoxes.add(new AxisAlignedBB(block.pos()));
                separateBlocks.add(block);
            }
            else if (mergeableSolidBlocks.isMergeableFullBlock(block.state())) {
                mergeableSolidBlocks.add(block.pos());
            }
            else separateBlocks.add(block);
        }

        //attach merged full-block boxes as solid collision shapes.
        mergeableSolidBlocks.forEachMergedBlockBox((box, mergedBlocks) -> this.attachBoxShape(box, false));

        //attach liquids and non-full-block shapes individually to preserve exact collision bounds.
        for (BlockSection.BlockData block : separateBlocks) {
            for (AxisAlignedBB box : getCollisionBoxes(world, block.pos(), block.state(), block.liquid())) {
                this.attachBoxShape(box, block.liquid());
            }
        }
    }

    /**
     * nothing to synchronize here, xd
     * */
    @Override
    public void synchronize(@NotNull BlockSection blockSection) {}

    /**
     * nothing to update either xd
     * */
    @Override
    public void updateBeforeSimulation(@NotNull World hostWorld, @NotNull List<PhysXBlockSectionBody> blockSectionsWithLiquids, double timeStep) {}

    @Override
    public void updateAfterSimulation() {}

    public boolean isLiquidBlockIntersecting(@NotNull AxisAlignedBB box) {
        for (AxisAlignedBB liquidBox : this.liquidBoxes) {
            if (box.intersects(liquidBox)) return true;
        }
        return false;
    }

    public boolean hasLiquidBlocks() {
        return !this.liquidBoxes.isEmpty();
    }

    private void attachBoxShape(AxisAlignedBB worldBox, boolean isLiquid) {
        PxShape shape = this.createBoxShape(worldBox, isLiquid ? this.liquidMaterial : this.blockMaterial);
        if (shape == null) return;

        if (isLiquid) {
            shape.setFlag(PxShapeFlagEnum.eSIMULATION_SHAPE, false);
            shape.setFlag(PxShapeFlagEnum.eTRIGGER_SHAPE, true);
            PhysXActor.LIQUID.setFilter(shape);
        }
        else PhysXActor.SOLID.setFilter(shape);

        double centerX = (worldBox.minX + worldBox.maxX) * 0.5D - this.identifier.getOriginX();
        double centerY = (worldBox.minY + worldBox.maxY) * 0.5D - this.identifier.getOriginY();
        double centerZ = (worldBox.minZ + worldBox.maxZ) * 0.5D - this.identifier.getOriginZ();
        PxTransform localPose = PhysXActorUtil.createTransform(centerX, centerY, centerZ);
        shape.setLocalPose(localPose);
        localPose.destroy();

        this.addShape(shape);
    }

    //---static helpers---
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
        catch (Throwable ignored) {
            if (state.getMaterial().blocksMovement()) return new AxisAlignedBB(pos);
        }

        return null;
    }

    //---other classes---
    public static final class Identifier extends AbstractPhysXCollisionObject.Identifier {
        @NotNull
        private final World world;
        private final int sectionX;
        private final int sectionY;
        private final int sectionZ;
        private final int contentsHash;

        public Identifier(@NotNull World world, int sectionX, int sectionY, int sectionZ, int contentsHash) {
            this.world = world;
            this.sectionX = sectionX;
            this.sectionY = sectionY;
            this.sectionZ = sectionZ;
            this.contentsHash = contentsHash;
        }

        private int getOriginX() {
            return this.sectionX << 4;
        }

        private int getOriginY() {
            return this.sectionY << 4;
        }

        private int getOriginZ() {
            return this.sectionZ << 4;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof Identifier that)) return false;
            return this.world == that.world
                    && this.sectionX == that.sectionX
                    && this.sectionY == that.sectionY
                    && this.sectionZ == that.sectionZ
                    && this.contentsHash == that.contentsHash;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(this.world);
            result = 31 * result + this.sectionX;
            result = 31 * result + this.sectionY;
            result = 31 * result + this.sectionZ;
            result = 31 * result + this.contentsHash;
            return result;
        }
    }
}
