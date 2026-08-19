package org.valkyrienskies.mod.common.ships.entity_interaction;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.anchored_mount.IShipAnchoredMount;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.network.MessageClearEntityShipRenderPosition;
import org.valkyrienskies.mod.common.network.MessageEntityShipRenderPosition;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.TransformedAABB;
import org.valkyrienskies.mod.common.util.VSMath;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * This class handles the logic of moving entities with the ships they're interacting with.
 * Includes entities on ships and entities pushed by ships.
 */
public class EntityDraggable {
    private static final Set<Entity> LAST_SHIP_LOCAL_RENDER_SYNC = new HashSet<>();

    /**
     * Moves entities such that they move with the ship below them.
     */
    public static void tickAddedVelocityForWorld(World world) {
        try {
            for (int i = 0; i < world.loadedEntityList.size(); i++) {
                Entity e = world.loadedEntityList.get(i);
                if (!e.isDead) {
                    addEntityVelocityFromShipBelow(e);
                    syncShipLocalRenderPositionToClients(e);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds the ship below velocity to entity.
     */
    private static void addEntityVelocityFromShipBelow(@NotNull final Entity entity) {
        IEntityShipDraggable draggable = entity.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
        if (draggable == null) return;

        final EntityShipMountData anchoredMountData = ValkyrienUtils.getAnchoredMountShipAndPos(entity);
        if (anchoredMountData.isMounted()) {
            draggable.setLastTouchedShip(anchoredMountData.getMountedShip().getShipData());
            draggable.setTicksSinceTouchedShip(0);
            draggable.setTicksPartOfGround(0);
            draggable.setAddedLinearVelocity(new Vector3d());
            draggable.setAddedYawVelocity(0);
            return;
        }

        final EntityShipMountData mountData = ValkyrienUtils.getMountedShipAndPos(entity);
        final ShipData lastShipTouchedPlayer = draggable.getLastTouchedShip();
        final int oldTicksSinceTouchedShip = draggable.getTicksSinceTouchedShip();
        final Vector3dc oldVelocityAdded = draggable.getAddedLinearVelocity();
        final double oldYawVelocityAdded = draggable.getAddedYawVelocity();

        if (lastShipTouchedPlayer == null || oldTicksSinceTouchedShip >= VSConfig.ticksToStickToShip) {
            if (entity.onGround) {
                // Player is on ground and not on a ship, therefore set their added velocity to 0.
                draggable.setAddedLinearVelocity(new Vector3d());
                draggable.setAddedYawVelocity(0);
            }
            else if (entity instanceof EntityPlayer player) {
                if (player.isCreative() && player.capabilities.isFlying) {
                    // If the player is flying, then slow down their added velocity significantly every tick
                    final Vector3dc newVelocityAdded = oldVelocityAdded.mul(0.95D, new Vector3d());
                    final double newYawVelocityAdded = oldYawVelocityAdded * 0.95D * 0.95D;

                    draggable.setAddedLinearVelocity(newVelocityAdded);
                    draggable.setAddedYawVelocity(newYawVelocityAdded);
                }
                else {
                    // Otherwise only slow down their added velocity slightly every tick
                    final Vector3dc newVelocityAdded = oldVelocityAdded.mul(0.99D, new Vector3d());
                    final double newYawVelocityAdded = oldYawVelocityAdded * 0.95D;

                    draggable.setAddedLinearVelocity(newVelocityAdded);
                    draggable.setAddedYawVelocity(newYawVelocityAdded);
                }
            }
        }
        else {
            final float rotYaw = entity.rotationYaw;
            final float rotPitch = entity.rotationPitch;
            final float prevYaw = entity.prevRotationYaw;
            final float prevPitch = entity.prevRotationPitch;

            final Vector3dc oldPos = new Vector3d(entity.posX, entity.posY, entity.posZ);

            final Matrix4d betweenTransform = ShipTransform.createTransform(
                    lastShipTouchedPlayer.getPrevTickShipTransform(), lastShipTouchedPlayer.getShipTransform());

            ValkyrienUtils.transformEntity(betweenTransform, entity, false);

            final Vector3dc newPos = new Vector3d(entity.posX, entity.posY, entity.posZ);

            // Move the entity back to its old position, the added velocity will be used
            // afterwards
            entity.setPosition(oldPos.x(), oldPos.y(), oldPos.z());
            final Vector3dc addedVel = newPos.sub(oldPos, new Vector3d());

            // Now compute the added yaw velocity
            entity.rotationYaw = rotYaw;
            entity.rotationPitch = rotPitch;
            entity.prevRotationYaw = prevYaw;
            entity.prevRotationPitch = prevPitch;

            // Ignore the pitch, calculate the look vector using only the yaw
            final Vector3d newLookYawVec;
            if (entity instanceof EntityLivingBase && !(entity instanceof EntityPlayer)) {
                newLookYawVec = new Vector3d(
                        -MathHelper.sin(-entity.getRotationYawHead() * 0.017453292F - (float) Math.PI),
                        0,
                        -MathHelper.cos(-entity.getRotationYawHead() * 0.017453292F - (float) Math.PI));
            }
            else {
                newLookYawVec = new Vector3d(
                        -MathHelper.sin(-entity.rotationYaw * 0.017453292F - (float) Math.PI),
                        0,
                        -MathHelper.cos(-entity.rotationYaw * 0.017453292F - (float) Math.PI));
            }

            // Transform the player look vector
            betweenTransform.transformDirection(newLookYawVec);

            // Calculate the yaw of the transformed player look vector
            final Tuple<Double, Double> newPlayerLookYawOnly = VSMath.getPitchYawFromVector(newLookYawVec);

            final double wrappedYaw = MathHelper.wrapDegrees(newPlayerLookYawOnly.getSecond());
            final double wrappedRotYaw;
            // We do this because entity.getLook() is calculated differently for EntityLivingBase, it uses
            // rotationYawHead instead of just rotationYaw.

            // if (entity instanceof EntityLivingBase && !(entity instanceof EntityPlayerSP)) {
            // [Changed because EntityPlayerSP is a 'client' class]
            if (entity instanceof EntityLivingBase && !(entity instanceof EntityPlayer)) {
                wrappedRotYaw = MathHelper.wrapDegrees(entity.getRotationYawHead());
            }
            else {
                wrappedRotYaw = MathHelper.wrapDegrees(entity.rotationYaw);
            }
            double yawDif = wrappedYaw - wrappedRotYaw;
            if (Math.abs(yawDif) > 180D) {
                if (yawDif < 0) yawDif += 360D;
                else yawDif -= 360D;
            }
            yawDif %= 360D;
            final double threshold = .1D;
            if (Math.abs(yawDif) < threshold) {
                yawDif = 0D;
            }
            draggable.setAddedLinearVelocity(addedVel.mul(1, new Vector3d()));
            draggable.setAddedYawVelocity(yawDif);
        }

        // Only run this code if we are adding extra velocity. This code is relatively expensive, so we don't want to run
        // it unless we have to.
        if (draggable.getAddedLinearVelocity().lengthSquared() > 0) {
            // Now that we've determined the added velocity, move the entity forward by that amount
            final boolean originallySneaking = entity.isSneaking();
            entity.setSneaking(false);

            // The added velocity vector of the player, except we have made sure that it won't push the player inside of a
            // solid block.
            final Vector3dc addedVelocityNoNoClip = applyAddedVelocity(draggable.getAddedLinearVelocity(), entity);
            draggable.setAddedLinearVelocity(addedVelocityNoNoClip);

            entity.setSneaking(originallySneaking);
        }

        // Add the yaw velocity to the player as well, because its possible for addedVelocity=0 and yawVel != 0
        final double addedYawVelocity = draggable.getAddedYawVelocity();
        if (!mountData.isMounted() && addedYawVelocity != 0) {
            entity.setRotationYawHead((float) (entity.getRotationYawHead() + addedYawVelocity));
            entity.rotationYaw += (float) addedYawVelocity;
        }
    }

    /**
     * Sends render-only ship-local positions for entities supported by ships and anchored chairs.
     * Clients use this to avoid vanilla's delayed global interpolation.
     */
    private static void syncShipLocalRenderPositionToClients(@NotNull final Entity entity) {
        if (entity.world.isRemote || entity instanceof EntityPlayer) return;

        final IEntityShipDraggable draggable = entity.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
        ShipData mountedShip = null;
        Vector3d localPosition = null;

        if (draggable != null) {
            final ShipData lastTouchedShip = draggable.getLastTouchedShip();
            final boolean shipContactIsActive = lastTouchedShip != null && draggable.getTicksSinceTouchedShip() < VSConfig.ticksToStickToShip;

            if (shipContactIsActive && isEntitySupportedByShip(entity, lastTouchedShip)) {
                mountedShip = lastTouchedShip;
                localPosition = new Vector3d(entity.posX, entity.posY, entity.posZ);
                mountedShip.getShipTransform().transformPosition(localPosition, TransformType.GLOBAL_TO_SUBSPACE);
            }
        }

        if (mountedShip == null) {
            final IShipAnchoredMount anchoredMount = entity.getCapability(VSCapabilityRegistry.VS_SHIP_ANCHORED_MOUNT, null);
            if (anchoredMount != null && (anchoredMount.isAnchoredToShip() || anchoredMount.tryAnchorMount(entity))) {
                final Optional<PhysicsObject> mountedPhysicsObject =
                        ValkyrienUtils.getPhysoManagingBlock(entity.world, anchoredMount.getLocalAnchorBlock());
                if (mountedPhysicsObject.isPresent()) {
                    mountedShip = mountedPhysicsObject.get().getShipData();
                    localPosition = new Vector3d(
                            anchoredMount.getLocalMountPos().x,
                            anchoredMount.getLocalMountPos().y,
                            anchoredMount.getLocalMountPos().z);
                }
            }
        }

        if (mountedShip == null && LAST_SHIP_LOCAL_RENDER_SYNC.remove(entity)) {
            //remove render position
            ValkyrienSkiesMod.physWrapperNetwork.sendToAllTracking(
                    new MessageClearEntityShipRenderPosition(entity),
                    entity
            );
        }
        else if (mountedShip != null) {
            //create render position
            LAST_SHIP_LOCAL_RENDER_SYNC.add(entity);
            ValkyrienSkiesMod.physWrapperNetwork.sendToAllTracking(
                    new MessageEntityShipRenderPosition(entity, mountedShip, localPosition),
                    entity
            );
        }
    }

    /**
     * Checks whether the entity is standing on collision owned by the ship.
     * This prevents ship-local render sync from applying to entities only pushed from the side.
     */
    private static boolean isEntitySupportedByShip(@NotNull final Entity entity, @NotNull final ShipData shipData) {
        final AxisAlignedBB entityBox = entity.getEntityBoundingBox();
        final double insetX = Math.clamp((entityBox.maxX - entityBox.minX) * 0.25D, 0.0D, 0.05D);
        final double insetZ = Math.clamp((entityBox.maxZ - entityBox.minZ) * 0.25D, 0.0D, 0.05D);
        final AxisAlignedBB supportProbe = new AxisAlignedBB(
                entityBox.minX + insetX,
                entityBox.minY - 0.15D,
                entityBox.minZ + insetZ,
                entityBox.maxX - insetX,
                entityBox.minY + 0.05D,
                entityBox.maxZ - insetZ
        );
        final AxisAlignedBB localProbe = new TransformedAABB(
                supportProbe,
                shipData.getShipTransform(),
                TransformType.GLOBAL_TO_SUBSPACE
        ).getEnclosedAABB();

        final int minX = MathHelper.floor(localProbe.minX);
        final int minY = MathHelper.floor(localProbe.minY);
        final int minZ = MathHelper.floor(localProbe.minZ);
        final int maxX = MathHelper.floor(localProbe.maxX + 1.0D);
        final int maxY = MathHelper.floor(localProbe.maxY + 1.0D);
        final int maxZ = MathHelper.floor(localProbe.maxZ + 1.0D);
        final List<AxisAlignedBB> collisionBoxes = new ArrayList<>();

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    final BlockPos localBlockPos = new BlockPos(x, y, z);
                    final Optional<ShipData> managingShip = ValkyrienUtils.getShipManagingBlock(entity.world, localBlockPos);
                    if (managingShip.isEmpty() || !managingShip.get().getUuid().equals(shipData.getUuid())) continue;

                    collisionBoxes.clear();
                    final IBlockState blockState = entity.world.getBlockState(localBlockPos);
                    try {
                        blockState.addCollisionBoxToList(entity.world, localBlockPos, localProbe, collisionBoxes, null, false);
                    }
                    catch (Throwable ignored) {
                        try {
                            final AxisAlignedBB fallbackBox = blockState.getCollisionBoundingBox(entity.world, localBlockPos);
                            if (fallbackBox != null && fallbackBox.offset(localBlockPos).intersects(localProbe)) return true;
                        }
                        catch (Throwable ignoredFallback) {
                            if (blockState.getMaterial().blocksMovement() && new AxisAlignedBB(localBlockPos).intersects(localProbe)) return true;
                        }
                    }
                    if (!collisionBoxes.isEmpty()) return true;
                }
            }
        }

        return false;
    }

    /**
     * Moves entity forward by addedVelocity, making sure not to clip through blocks
     * @param addedVelocity The velocity added to this entity by the ship they've touched
     * @param entity The entity to be moved
     * @return The vector that the entity actually moved, after detecting collisions with blocks in the world.
     */
    public static Vector3dc applyAddedVelocity(final Vector3dc addedVelocity, final Entity entity) {
        double x = addedVelocity.x();
        double y = addedVelocity.y();
        double z = addedVelocity.z();

        if (entity.isInWeb) {
            entity.isInWeb = false;
            x *= 0.25D;
            y *= 0.05000000074505806D;
            z *= 0.25D;
            entity.motionX = 0.0D;
            entity.motionY = 0.0D;
            entity.motionZ = 0.0D;
        }

        double d2 = x;
        double d3 = y;
        double d4 = z;

        AxisAlignedBB potentialCrashBB = entity.getEntityBoundingBox().offset(x, y, z);

        // TODO: This is a band aid not a solution
        if (potentialCrashBB.getAverageEdgeLength() > 999999) {
            // The player went too fast, something is wrong.
            System.err.println("Entity with ID " + entity.getEntityId()
                    + " went way too fast! Reseting its position.");
            return new Vector3d();
        }

        List<AxisAlignedBB> list1 = entity.world
                .getCollisionBoxes(entity, potentialCrashBB);
        AxisAlignedBB axisalignedbb = entity.getEntityBoundingBox();

        if (y != 0.0D) {
            int k = 0;

            for (int l = list1.size(); k < l; ++k) {
                y = list1.get(k).calculateYOffset(entity.getEntityBoundingBox(), y);
            }

            entity.setEntityBoundingBox(
                    entity.getEntityBoundingBox().offset(0.0D, y, 0.0D));
        }

        if (x != 0.0D) {
            int j5 = 0;

            for (int l5 = list1.size(); j5 < l5; ++j5) {
                x = list1.get(j5).calculateXOffset(entity.getEntityBoundingBox(), x);
            }

            if (x != 0.0D) {
                entity.setEntityBoundingBox(entity.getEntityBoundingBox().offset(x, 0.0D, 0.0D));
            }
        }

        if (z != 0.0D) {
            int k5 = 0;

            for (int i6 = list1.size(); k5 < i6; ++k5) {
                z = list1.get(k5).calculateZOffset(entity.getEntityBoundingBox(), z);
            }

            if (z != 0.0D) {
                entity.setEntityBoundingBox(entity.getEntityBoundingBox().offset(0.0D, 0.0D, z));
            }
        }

        boolean flag = entity.onGround || d3 != y && d3 < 0.0D;

        if (entity.stepHeight > 0.0F && flag && (d2 != x || d4 != z)) {
            double d14 = x;
            double d6 = y;
            double d7 = z;
            AxisAlignedBB axisalignedbb1 = entity.getEntityBoundingBox();
            entity.setEntityBoundingBox(axisalignedbb);
            y = entity.stepHeight;
            List<AxisAlignedBB> list = entity.world.getCollisionBoxes(
                    entity,
                    entity.getEntityBoundingBox().offset(d2, y, d4)
            );
            AxisAlignedBB axisalignedbb2 = entity.getEntityBoundingBox();
            AxisAlignedBB axisalignedbb3 = axisalignedbb2.offset(d2, 0.0D, d4);
            double d8 = y;
            int j1 = 0;

            for (int k1 = list.size(); j1 < k1; ++j1) {
                d8 = list.get(j1).calculateYOffset(axisalignedbb3, d8);
            }

            axisalignedbb2 = axisalignedbb2.offset(0.0D, d8, 0.0D);
            double d18 = d2;
            int l1 = 0;

            for (int i2 = list.size(); l1 < i2; ++l1) {
                d18 = list.get(l1).calculateXOffset(axisalignedbb2, d18);
            }

            axisalignedbb2 = axisalignedbb2.offset(d18, 0.0D, 0.0D);
            double d19 = d4;
            int j2 = 0;

            for (int k2 = list.size(); j2 < k2; ++j2) {
                d19 = list.get(j2).calculateZOffset(axisalignedbb2, d19);
            }

            axisalignedbb2 = axisalignedbb2.offset(0.0D, 0.0D, d19);
            AxisAlignedBB axisalignedbb4 = entity.getEntityBoundingBox();
            double d20 = y;
            int l2 = 0;

            for (int i3 = list.size(); l2 < i3; ++l2) {
                d20 = list.get(l2).calculateYOffset(axisalignedbb4, d20);
            }

            axisalignedbb4 = axisalignedbb4.offset(0.0D, d20, 0.0D);
            double d21 = d2;
            int j3 = 0;

            for (int k3 = list.size(); j3 < k3; ++j3) {
                d21 = list.get(j3).calculateXOffset(axisalignedbb4, d21);
            }

            axisalignedbb4 = axisalignedbb4.offset(d21, 0.0D, 0.0D);
            double d22 = d4;
            int l3 = 0;

            for (int i4 = list.size(); l3 < i4; ++l3) {
                d22 = list.get(l3).calculateZOffset(axisalignedbb4, d22);
            }

            axisalignedbb4 = axisalignedbb4.offset(0.0D, 0.0D, d22);
            double d23 = d18 * d18 + d19 * d19;
            double d9 = d21 * d21 + d22 * d22;

            if (d23 > d9) {
                x = d18;
                z = d19;
                y = -d8;
                entity.setEntityBoundingBox(axisalignedbb2);
            } else {
                x = d21;
                z = d22;
                y = -d20;
                entity.setEntityBoundingBox(axisalignedbb4);
            }

            int j4 = 0;

            for (int k4 = list.size(); j4 < k4; ++j4) {
                y = list.get(j4).calculateYOffset(entity.getEntityBoundingBox(), y);
            }

            entity.setEntityBoundingBox(
                    entity.getEntityBoundingBox().offset(0.0D, y, 0.0D));

            if (d14 * d14 + d7 * d7 >= x * x + z * z) {
                x = d14;
                y = d6;
                z = d7;
                entity.setEntityBoundingBox(axisalignedbb1);
            }
        }

        entity.resetPositionToBB();
        return new Vector3d(x, y, z);
    }
}
