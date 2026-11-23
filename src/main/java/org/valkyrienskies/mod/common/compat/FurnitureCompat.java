package org.valkyrienskies.mod.common.compat;

import bl4ckscor3.mod.sit.EntitySit;
import com.mrcrayfish.furniture.entity.EntitySeat;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import rustic.common.blocks.BlockChair;
import valkyrienwarfare.api.event.ShipAnchoredEntityDismountEvent;
import vazkii.quark.vanity.feature.SitInStairs;

public class FurnitureCompat {
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onShipDismount(@NotNull ShipAnchoredEntityDismountEvent event) {
        Entity mount = event.getMount();
        boolean furnitureSeat = Loader.isModLoaded("cfm") && mount instanceof EntitySeat
                || Loader.isModLoaded("rustic") && mount instanceof BlockChair.EntityChair
                || Loader.isModLoaded("sit") && mount instanceof EntitySit
                || Loader.isModLoaded("quark") && mount instanceof SitInStairs.Seat
                || Loader.isModLoaded("bibliocraft") && mount instanceof jds.bibliocraft.entity.EntitySeat;
        //vanilla dismount method but more friendly for ships
        if (furnitureSeat) {
            EntityLivingBase rider = event.getRider();
            Vec3d localSeatPosition = event.getLocalMountPos();
            World world = mount.world;
            double riderY = localSeatPosition.y + mount.getMountedYOffset() + rider.getYOffset();
            Vec3d dismountPosition = localSeatPosition.add(0D, mount.height, 0D);
            EnumFacing forward = mount.getAdjustedHorizontalFacing();
            if (forward != null) {
                EnumFacing sideways = forward.rotateY();
                int[][] offsets = {{0, 1}, {0, -1}, {-1, 1}, {-1, -1}, {1, 1}, {1, -1}, {-1, 0}, {1, 0}, {0, 1}};
                double centerX = Math.floor(localSeatPosition.x) + 0.5D;
                double centerZ = Math.floor(localSeatPosition.z) + 0.5D;
                double halfWidth = rider.width / 2D;
                AxisAlignedBB riderBounds = new AxisAlignedBB(
                        centerX - halfWidth, localSeatPosition.y, centerZ - halfWidth,
                        centerX + halfWidth, Math.floor(localSeatPosition.y) + rider.height, centerZ + halfWidth
                );

                for (int[] offset : offsets) {
                    double offsetX = forward.getXOffset() * offset[0] + sideways.getXOffset() * offset[1];
                    double offsetZ = forward.getZOffset() * offset[0] + sideways.getZOffset() * offset[1];
                    double x = centerX + offsetX;
                    double z = centerZ + offsetZ;
                    AxisAlignedBB candidateBounds = riderBounds.offset(offsetX, 0D, offsetZ);
                    if (!world.collidesWithAnyBlock(candidateBounds)) {
                        BlockPos supportingBlock = new BlockPos(x, riderY, z);
                        if (world.getBlockState(supportingBlock).isSideSolid(world, supportingBlock, EnumFacing.UP)) {
                            dismountPosition = new Vec3d(x, riderY + 1D, z);
                            break;
                        }

                        supportingBlock = new BlockPos(x, riderY - 1D, z);
                        if (world.getBlockState(supportingBlock).isSideSolid(world, supportingBlock, EnumFacing.UP)
                                || world.getBlockState(supportingBlock).getMaterial() == Material.WATER
                        ) {
                            dismountPosition = new Vec3d(x, riderY + 1D, z);
                        }
                    }
                    else {
                        BlockPos supportingBlock = new BlockPos(x, riderY + 1D, z);
                        if (!world.collidesWithAnyBlock(candidateBounds.offset(0D, 1D, 0D))
                                && world.getBlockState(supportingBlock).isSideSolid(world, supportingBlock, EnumFacing.UP)
                        ) {
                            dismountPosition = new Vec3d(x, riderY + 2D, z);
                        }
                    }
                }
            }
            event.setLocalDismountPos(dismountPosition);
        }
    }
}
