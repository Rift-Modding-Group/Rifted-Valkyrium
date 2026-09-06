package org.valkyrienskies.mod.common.ships.entity_interaction;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.anchored_mount.IShipAnchoredMount;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.ShipLocalEntityMovementData;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.VSMath;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import org.valkyrienskies.api.TransformType;

import java.util.List;
import java.util.Optional;

/**
 * This class handles the logic of moving entities with the ships they're interacting with.
 * Includes entities on ships and entities pushed by ships.
 */
public class EntityDraggable {
    /**
     * Moves entities such that they move with the ship below them.
     */
    public static void tickAddedVelocityForWorld(World world) {
        try {
            for (Entity entity : world.loadedEntityList) {
                if (entity.isDead) continue;
                addEntityVelocityFromShipBelow(entity);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the loaded ship currently carrying a server-authoritative entity, if one exists.
     */
    @Nullable
    public static ShipData getActiveShip(@NotNull Entity entity) {
        if (entity.isRiding()) return null;

        IEntityShipDraggable draggable = entity.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
        if (draggable == null || draggable.getLastTouchedShip() == null || draggable.getTicksSinceTouchedShip() >= VSConfig.ticksToStickToShip) {
            return null;
        }

        IPhysObjectWorld physObjectWorld = ValkyrienUtils.getPhysObjWorld(entity.world);
        if (physObjectWorld == null) return null;

        PhysicsObject physicsObject = physObjectWorld.getPhysObjectFromUUID(draggable.getLastTouchedShip().getUuid());
        return physicsObject == null ? null : physicsObject.getShipData();
    }

    /**
     * Returns whether this client entity is currently driven by a server ship-local movement stream.
     */
    public static boolean isUsingShipLocalMovement(@NotNull Entity entity) {
        IEntityShipDraggable draggable = entity.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
        if (draggable == null) return false;

        ShipLocalEntityMovementData movementData = draggable.getShipLocalMovementData();
        return movementData != null && movementData.isActive();
    }

    /**
     * Adds the ship below velocity to entity.
     */
    private static void addEntityVelocityFromShipBelow(@NotNull Entity entity) {
        IEntityShipDraggable draggable = entity.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
        if (draggable == null) return;

        ShipLocalEntityMovementData shipLocalMovementData = draggable.getShipLocalMovementData();
        if (entity.world.isRemote && shipLocalMovementData != null && shipLocalMovementData.isActive()) {
            if (entity.isRiding() || entity.canPassengerSteer()) {
                shipLocalMovementData.clear();
            }
            else {
                IPhysObjectWorld physObjectWorld = ValkyrienUtils.getPhysObjWorld(entity.world);
                PhysicsObject physicsObject = physObjectWorld == null || shipLocalMovementData.getShipUuid() == null
                        ? null : physObjectWorld.getPhysObjectFromUUID(shipLocalMovementData.getShipUuid());
                if (physicsObject == null) return;

                ShipTransform currentShipTransform = physicsObject.getShipTransformationManager().getCurrentTickTransform();
                shipLocalMovementData.initializeIfNeeded(
                        currentShipTransform,
                        new Vector3d(entity.posX, entity.posY, entity.posZ),
                        entity.rotationYaw,
                        entity.rotationPitch,
                        entity.getRotationYawHead()
                );

                Vector3d oldWorldPosition = new Vector3d(entity.posX, entity.posY, entity.posZ);
                float oldYaw = entity.rotationYaw;
                float oldHeadYaw = entity.getRotationYawHead();
                shipLocalMovementData.advance();
                Vector3d worldPosition = shipLocalMovementData.getWorldPosition(currentShipTransform, 1D);
                entity.setPosition(worldPosition.x, worldPosition.y, worldPosition.z);
                double worldYaw = shipLocalMovementData.getWorldYaw(currentShipTransform, 1D);
                entity.rotationYaw = oldYaw + MathHelper.wrapDegrees((float) (worldYaw - oldYaw));
                entity.rotationPitch = (float) shipLocalMovementData.getPitch(1D);
                double worldHeadYaw = shipLocalMovementData.getWorldHeadYaw(currentShipTransform, 1D);
                entity.setRotationYawHead(oldHeadYaw + MathHelper.wrapDegrees((float) (worldHeadYaw - oldHeadYaw)));
                entity.onGround = shipLocalMovementData.isOnGround();
                float yawMovement = MathHelper.wrapDegrees(entity.rotationYaw - oldYaw);

                if (entity instanceof EntityLivingBase livingEntity) {
                    livingEntity.renderYawOffset += yawMovement;

                    Vector3d relativeMovement = shipLocalMovementData.getRelativeMovement();
                    float targetLimbSwingAmount = (float) Math.sqrt(
                            relativeMovement.x * relativeMovement.x + relativeMovement.z * relativeMovement.z
                    ) * 4f;
                    targetLimbSwingAmount = Math.min(targetLimbSwingAmount, 1F);
                    float correctedLimbSwingAmount = livingEntity.prevLimbSwingAmount
                            + (targetLimbSwingAmount - livingEntity.prevLimbSwingAmount) * 0.4F;
                    livingEntity.limbSwing += correctedLimbSwingAmount - livingEntity.limbSwingAmount;
                    livingEntity.limbSwingAmount = correctedLimbSwingAmount;
                }

                updatePassengersAfterCarrierMovement(entity, yawMovement);

                if (shipLocalMovementData.hasVelocity()) {
                    Vector3d worldVelocity = shipLocalMovementData.getWorldVelocity(currentShipTransform);
                    entity.setVelocity(worldVelocity.x, worldVelocity.y, worldVelocity.z);
                }

                draggable.setLastTouchedShip(physicsObject.getShipData());
                draggable.setTicksSinceTouchedShip(0);
                draggable.setAddedLinearVelocity(worldPosition.sub(oldWorldPosition, new Vector3d()));
                draggable.setAddedYawVelocity(yawMovement);
                return;
            }
        }

        ShipData touchedShip = draggable.getLastTouchedShip();
        if (touchedShip != null) {
            IPhysObjectWorld physObjectWorld = ValkyrienUtils.getPhysObjWorld(entity.world);
            if (physObjectWorld == null || physObjectWorld.getPhysObjectFromUUID(touchedShip.getUuid()) == null) {
                draggable.setLastTouchedShip(null);
            }
        }

        //For chairs from other mods
        IShipAnchoredMount anchoredMount = entity.getCapability(VSCapabilityRegistry.VS_SHIP_ANCHORED_MOUNT, null);
        boolean snapAnchorPosition = draggable.getLastTouchedShip() == null;
        boolean wasAnchored = anchoredMount != null && anchoredMount.isAnchoredToShip();
        if (anchoredMount != null && (wasAnchored || anchoredMount.tryAnchorMount(entity))) {
            Optional<PhysicsObject> mountedShip = ValkyrienUtils.getPhysoManagingBlock(entity.world, anchoredMount.getLocalAnchorBlock());
            if (mountedShip.isPresent()) {
                PhysicsObject physicsObject = mountedShip.get();
                Vector3d localMountPosition = new Vector3d(
                        anchoredMount.getLocalMountPos().x,
                        anchoredMount.getLocalMountPos().y,
                        anchoredMount.getLocalMountPos().z
                );
                Vector3d globalMountPosition = new Vector3d(localMountPosition);
                physicsObject.getShipTransformationManager().getCurrentTickTransform()
                        .transformPosition(globalMountPosition, TransformType.SUBSPACE_TO_GLOBAL);
                entity.setPosition(globalMountPosition.x, globalMountPosition.y, globalMountPosition.z);
                Vector3d previousGlobalMountPosition = new Vector3d(localMountPosition);
                physicsObject.getPrevTickShipTransform()
                        .transformPosition(previousGlobalMountPosition, TransformType.SUBSPACE_TO_GLOBAL);
                if (snapAnchorPosition) {
                    entity.prevPosX = previousGlobalMountPosition.x;
                    entity.prevPosY = previousGlobalMountPosition.y;
                    entity.prevPosZ = previousGlobalMountPosition.z;
                }
                entity.lastTickPosX = previousGlobalMountPosition.x;
                entity.lastTickPosY = previousGlobalMountPosition.y;
                entity.lastTickPosZ = previousGlobalMountPosition.z;

                updatePassengersAfterCarrierMovement(entity, 0F);

                draggable.setLastTouchedShip(physicsObject.getShipData());
                draggable.setTicksSinceTouchedShip(0);
                draggable.setTicksPartOfGround(0);
                draggable.setAddedLinearVelocity(new Vector3d(globalMountPosition).sub(
                        entity.lastTickPosX,
                        entity.lastTickPosY,
                        entity.lastTickPosZ
                ));
                draggable.setAddedYawVelocity(0);
                return;
            }
        }

        //fix to ensure players sleeping on beds on ships stay in place
        EntityShipMountData sleepingPlayerMountData = ValkyrienUtils.getSleepingPlayerShipAndPos(entity);
        if (sleepingPlayerMountData.isMounted()) {
            Vector3dc oldPosition = new Vector3d(entity.posX, entity.posY, entity.posZ);
            Vector3d globalSleepingPosition = new Vector3d(
                    sleepingPlayerMountData.mountPos().x,
                    sleepingPlayerMountData.mountPos().y,
                    sleepingPlayerMountData.mountPos().z
            );
            sleepingPlayerMountData.mountedShip().getShipTransformationManager()
                    .getCurrentTickTransform()
                    .transformPosition(globalSleepingPosition, TransformType.SUBSPACE_TO_GLOBAL);
            entity.setPosition(globalSleepingPosition.x, globalSleepingPosition.y, globalSleepingPosition.z);

            entity.motionX = 0D;
            entity.motionY = 0D;
            entity.motionZ = 0D;

            Vector3dc shipAddedVelocity = globalSleepingPosition.sub(oldPosition, new Vector3d());
            draggable.setLastTouchedShip(sleepingPlayerMountData.mountedShip().getShipData());
            draggable.setTicksSinceTouchedShip(0);
            draggable.setTicksPartOfGround(0);
            draggable.setAddedLinearVelocity(shipAddedVelocity);
            draggable.setAddedYawVelocity(0);
            return;
        }

        //fix to ensure players sitting on modded chairs on ships stay in place
        EntityShipMountData anchoredMountData = ValkyrienUtils.getAnchoredMountShipAndPos(entity);
        if (anchoredMountData.isMounted()) {
            draggable.setLastTouchedShip(anchoredMountData.mountedShip().getShipData());
            draggable.setTicksSinceTouchedShip(0);
            draggable.setTicksPartOfGround(0);
            draggable.setAddedLinearVelocity(new Vector3d());
            draggable.setAddedYawVelocity(0);
            return;
        }

        //for the mod's own chairs
        EntityShipMountData mountData = ValkyrienUtils.getMountedShipAndPos(entity);
        if (entity.isRiding()) {
            ShipData ridingShip = mountData.isMounted() ? mountData.mountedShip().getShipData() : null;
            draggable.setLastTouchedShip(ridingShip);
            draggable.setTicksSinceTouchedShip(ridingShip == null ? VSConfig.ticksToStickToShip : 0);
            draggable.setAddedLinearVelocity(new Vector3d());
            draggable.setAddedYawVelocity(0D);
            return;
        }

        ShipData lastShipTouchedPlayer = draggable.getLastTouchedShip();
        int oldTicksSinceTouchedShip = draggable.getTicksSinceTouchedShip();
        Vector3dc oldVelocityAdded = draggable.getAddedLinearVelocity();
        double oldYawVelocityAdded = draggable.getAddedYawVelocity();

        if (lastShipTouchedPlayer == null || oldTicksSinceTouchedShip >= VSConfig.ticksToStickToShip) {
            if (entity.onGround) {
                // Player is on ground and not on a ship, therefore set their added velocity to 0.
                draggable.setAddedLinearVelocity(new Vector3d());
                draggable.setAddedYawVelocity(0);
            }
            else if (entity instanceof EntityPlayer player) {
                if (player.isCreative() && player.capabilities.isFlying) {
                    // If the player is flying, then slow down their added velocity significantly every tick
                    Vector3dc newVelocityAdded = oldVelocityAdded.mul(0.95D, new Vector3d());
                    double newYawVelocityAdded = oldYawVelocityAdded * 0.95D * 0.95D;

                    draggable.setAddedLinearVelocity(newVelocityAdded);
                    draggable.setAddedYawVelocity(newYawVelocityAdded);
                }
                else {
                    // Otherwise only slow down their added velocity slightly every tick
                    Vector3dc newVelocityAdded = oldVelocityAdded.mul(0.99D, new Vector3d());
                    double newYawVelocityAdded = oldYawVelocityAdded * 0.95D;

                    draggable.setAddedLinearVelocity(newVelocityAdded);
                    draggable.setAddedYawVelocity(newYawVelocityAdded);
                }
            }
        }
        else {
            float rotationYaw = entity.rotationYaw;
            float rotationPitch = entity.rotationPitch;
            float previousRotationYaw = entity.prevRotationYaw;
            float previousRotationPitch = entity.prevRotationPitch;
            Vector3dc oldPosition = new Vector3d(entity.posX, entity.posY, entity.posZ);

            Matrix4d betweenTransform = ShipTransform.createTransform(
                    lastShipTouchedPlayer.getPrevTickShipTransform(), lastShipTouchedPlayer.getShipTransform()
            );
            ValkyrienUtils.transformEntity(betweenTransform, entity, false);
            Vector3dc transformedPosition = new Vector3d(entity.posX, entity.posY, entity.posZ);

            // Apply the carrier displacement through collision handling below. Evaluating it at the tick endpoint keeps
            // the render-time curve continuous with the entity's actual current position.
            entity.setPosition(oldPosition.x(), oldPosition.y(), oldPosition.z());
            Vector3dc addedVelocity = transformedPosition.sub(oldPosition, new Vector3d());
            entity.rotationYaw = rotationYaw;
            entity.rotationPitch = rotationPitch;
            entity.prevRotationYaw = previousRotationYaw;
            entity.prevRotationPitch = previousRotationPitch;

            // Ignore the pitch, calculate the look vector using only the yaw
            Vector3d newLookYawVec;
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
            Tuple<Double, Double> newPlayerLookYawOnly = VSMath.getPitchYawFromVector(newLookYawVec);

            double wrappedYaw = MathHelper.wrapDegrees(newPlayerLookYawOnly.getSecond());
            double wrappedRotYaw;
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
            double threshold = .1D;
            if (Math.abs(yawDif) < threshold) {
                yawDif = 0D;
            }
            draggable.setAddedLinearVelocity(addedVelocity);
            draggable.setAddedYawVelocity(yawDif);
        }

        // Only run this code if we are adding extra velocity. This code is relatively expensive, so we don't want to run
        // it unless we have to.
        if (draggable.getAddedLinearVelocity().lengthSquared() > 0) {
            // Now that we've determined the added velocity, move the entity forward by that amount
            boolean originallySneaking = entity.isSneaking();
            entity.setSneaking(false);

            // The added velocity vector of the player, except we have made sure that it won't push the player inside of a
            // solid block.
            Vector3dc addedVelocityNoNoClip = applyAddedVelocity(draggable.getAddedLinearVelocity(), entity);
            draggable.setAddedLinearVelocity(addedVelocityNoNoClip);

            entity.setSneaking(originallySneaking);
        }

        // Add the yaw velocity to the player as well, because its possible for addedVelocity=0 and yawVel != 0
        double addedYawVelocity = draggable.getAddedYawVelocity();
        float appliedYawMovement = 0F;
        if (!mountData.isMounted() && addedYawVelocity != 0) {
            entity.setRotationYawHead((float) (entity.getRotationYawHead() + addedYawVelocity));
            entity.rotationYaw += (float) addedYawVelocity;
            appliedYawMovement = (float) addedYawVelocity;
            if (entity instanceof EntityLivingBase livingEntity) {
                livingEntity.renderYawOffset += appliedYawMovement;
            }
        }

        if (!entity.getPassengers().isEmpty() && (draggable.getAddedLinearVelocity().lengthSquared() > 0D || appliedYawMovement != 0F)) {
            updatePassengersAfterCarrierMovement(entity, appliedYawMovement);
        }
    }

    /**
     * Repositions every rider in a carrier hierarchy and applies the carrier's ship-induced yaw exactly once.
     */
    private static void updatePassengersAfterCarrierMovement(@NotNull Entity carrier, float yawMovement) {
        for (Entity passenger : carrier.getPassengers()) {
            passenger.rotationYaw += yawMovement;
            passenger.setRotationYawHead(passenger.getRotationYawHead() + yawMovement);
            if (passenger instanceof EntityLivingBase livingPassenger) {
                livingPassenger.renderYawOffset += yawMovement;
            }
            updatePassengerPosition(carrier, passenger);
            updatePassengersAfterCarrierMovement(passenger, yawMovement);
        }
    }

    /**
     * Computes the render-time entity position after interpolating its non-ship movement in the ship's frame.
     */
    public static Vector3d getShipAdjustedRenderPosition(
            Vector3dc previousPosition,
            Vector3dc currentPosition,
            Vector3dc addedShipMovement,
            ShipTransform previousShipTransform,
            ShipTransform renderShipTransform,
            double partialTicks
    ) {
        double entityMovementX = currentPosition.x() - addedShipMovement.x() - previousPosition.x();
        double entityMovementY = currentPosition.y() - addedShipMovement.y() - previousPosition.y();
        double entityMovementZ = currentPosition.z() - addedShipMovement.z() - previousPosition.z();

        Vector3d renderPosition = new Vector3d(previousPosition).add(
                entityMovementX * partialTicks,
                entityMovementY * partialTicks,
                entityMovementZ * partialTicks
        );
        previousShipTransform.transformPosition(renderPosition, TransformType.GLOBAL_TO_SUBSPACE);
        renderShipTransform.transformPosition(renderPosition, TransformType.SUBSPACE_TO_GLOBAL);
        return renderPosition;
    }

    /**
     * Updates a rider from its carrier's current position without applying a vehicle's orientation callback twice.
     */
    public static void updatePassengerPosition(@NotNull Entity carrier, @NotNull Entity passenger) {
        float carrierRotationYaw = carrier.rotationYaw;
        float carrierRotationPitch = carrier.rotationPitch;
        float carrierPreviousRotationYaw = carrier.prevRotationYaw;
        float carrierPreviousRotationPitch = carrier.prevRotationPitch;
        EntityLivingBase livingCarrier = carrier instanceof EntityLivingBase livingEntity ? livingEntity : null;
        float carrierRenderYawOffset = livingCarrier == null ? 0F : livingCarrier.renderYawOffset;
        float carrierPreviousRenderYawOffset = livingCarrier == null ? 0f : livingCarrier.prevRenderYawOffset;
        float carrierRotationYawHead = livingCarrier == null ? 0f : livingCarrier.rotationYawHead;
        float carrierPreviousRotationYawHead = livingCarrier == null ? 0f : livingCarrier.prevRotationYawHead;

        float rotationYaw = passenger.rotationYaw;
        float rotationPitch = passenger.rotationPitch;
        float previousRotationYaw = passenger.prevRotationYaw;
        float previousRotationPitch = passenger.prevRotationPitch;
        EntityLivingBase livingPassenger = passenger instanceof EntityLivingBase livingEntity ? livingEntity : null;
        float renderYawOffset = livingPassenger == null ? 0f : livingPassenger.renderYawOffset;
        float previousRenderYawOffset = livingPassenger == null ? 0f : livingPassenger.prevRenderYawOffset;
        float rotationYawHead = livingPassenger == null ? 0f : livingPassenger.rotationYawHead;
        float previousRotationYawHead = livingPassenger == null ? 0f : livingPassenger.prevRotationYawHead;

        carrier.updatePassenger(passenger);

        carrier.rotationYaw = carrierRotationYaw;
        carrier.rotationPitch = carrierRotationPitch;
        carrier.prevRotationYaw = carrierPreviousRotationYaw;
        carrier.prevRotationPitch = carrierPreviousRotationPitch;
        if (livingCarrier != null) {
            livingCarrier.renderYawOffset = carrierRenderYawOffset;
            livingCarrier.prevRenderYawOffset = carrierPreviousRenderYawOffset;
            livingCarrier.rotationYawHead = carrierRotationYawHead;
            livingCarrier.prevRotationYawHead = carrierPreviousRotationYawHead;
        }

        passenger.rotationYaw = rotationYaw;
        passenger.rotationPitch = rotationPitch;
        passenger.prevRotationYaw = previousRotationYaw;
        passenger.prevRotationPitch = previousRotationPitch;
        if (livingPassenger != null) {
            livingPassenger.renderYawOffset = renderYawOffset;
            livingPassenger.prevRenderYawOffset = previousRenderYawOffset;
            livingPassenger.rotationYawHead = rotationYawHead;
            livingPassenger.prevRotationYawHead = previousRotationYawHead;
        }
    }

    /**
     * Moves entity forward by addedVelocity, making sure not to clip through blocks
     * @param addedVelocity The velocity added to this entity by the ship they've touched
     * @param entity The entity to be moved
     * @return The vector that the entity actually moved, after detecting collisions with blocks in the world.
     */
    public static Vector3dc applyAddedVelocity(Vector3dc addedVelocity, Entity entity) {
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
