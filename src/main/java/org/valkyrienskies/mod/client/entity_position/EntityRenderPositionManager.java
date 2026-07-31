package org.valkyrienskies.mod.client.entity_position;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityShipMountData;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransformationManager;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.JOML;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Position-only client carrier synchronization.
 *
 * <p>Vanilla remains responsible for independent entity movement. Ship motion
 * updates entity positions and position targets after living updates, never
 * entity orientation.</p>
 */
public class EntityRenderPositionManager {
    private static final int CLIENT_SUPPORT_MISS_TICKS = 5;
    private static final double INTENTIONAL_SEPARATION_VELOCITY = 0.08D;
    private static final double MAX_CLIENT_CARRIER_STEP_SQUARED = 4096D;

    private static final WeakHashMap<Entity, EntityRenderPositionBackup> renderPositionBackups = new WeakHashMap<>();
    private static final WeakHashMap<Entity, ShipLocalEntityRenderData> remoteRenderData = new WeakHashMap<>();
    private static final WeakHashMap<Entity, CarrierYawRenderState> carrierYawRenderStates = new WeakHashMap<>();
    private static final WeakHashMap<Entity, ClientPlayerRenderState> clientPlayerRenderStates = new WeakHashMap<>();

    public static void clearRenderPositionBackups() {
        renderPositionBackups.clear();
    }

    public static void restoreRenderPositionBackups(@NotNull World world) {
        for (final Entity entity : world.getLoadedEntityList()) {
            EntityRenderPositionBackup backup = renderPositionBackups.get(entity);
            if (backup == null) continue;

            entity.posX = backup.posX();
            entity.posY = backup.posY();
            entity.posZ = backup.posZ();
            entity.lastTickPosX = backup.lastTickPosX();
            entity.lastTickPosY = backup.lastTickPosY();
            entity.lastTickPosZ = backup.lastTickPosZ();
            entity.prevPosX = backup.prevPosX();
            entity.prevPosY = backup.prevPosY();
            entity.prevPosZ = backup.prevPosZ();
            entity.setEntityBoundingBox(backup.boundingBox());
        }
    }

    public static void removeShipLocalRenderData(@NotNull Entity entity) {
        remoteRenderData.remove(entity);
        carrierYawRenderStates.remove(entity);
    }

    public static void queueShipLocalRenderData(@NotNull Entity entity, @NotNull UUID shipUuid, Vector3dc localPosition, long updateTick) {
        ShipLocalEntityRenderData renderData = remoteRenderData.get(entity);
        boolean resetCarrierYaw = renderData == null || !renderData.getShipUuid().equals(shipUuid);
        remoteRenderData.put(entity, new ShipLocalEntityRenderData(shipUuid, localPosition, updateTick));

        CarrierYawRenderState state = carrierYawRenderStates.get(entity);
        if (resetCarrierYaw || state == null || !state.shipUuid.equals(shipUuid)) {
            PhysicsObject ship = getShip(entity.world, shipUuid);
            if (ship != null) {
                carrierYawRenderStates.put(
                        entity,
                        new CarrierYawRenderState(
                                shipUuid,
                                new Quaterniond(ship.getShipTransformationManager()
                                                .getCurrentTickTransform()
                                                .rotationQuaternion(TransformType.SUBSPACE_TO_GLOBAL)
                                )
                        )
                );
            }
            else {
                carrierYawRenderStates.remove(entity);
            }
        }

        remapRemoteInterpolationTarget(entity);
    }

    public static void remapRemoteInterpolationTarget(@NotNull Entity entity) {
        if (!(entity instanceof EntityLivingBase living)) return;

        ShipLocalEntityRenderData renderData = remoteRenderData.get(entity);
        if (renderData == null || renderData.isExpired(entity.world)) return;

        PhysicsObject ship = getShip(entity.world, renderData.getShipUuid());
        if (ship == null) return;

        Vector3d worldTarget = toWorld(
                ship.getShipTransformationManager().getCurrentTickTransform(),
                renderData.localTarget
        );
        setPositionTarget(living, worldTarget);
    }

    /**
     * Captures the local player's vanilla post-movement position against the old
     * client ship transform.
     */
    public static void captureClientPlayerBeforeShipTick(@NotNull Entity entity, @NotNull World world) {
        if (ValkyrienUtils.getMountedShipAndPos(entity).isMounted()) {
            clientPlayerRenderStates.remove(entity);
            return;
        }

        ClientPlayerRenderState previousState = clientPlayerRenderStates.get(entity);
        SupportLease previousLease =
                previousState == null ? null : previousState.supportLease;
        IEntityShipDraggable draggable = entity.getCapability(
                VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE,
                null
        );
        ShipData contactedShip =
                draggable == null ? null : draggable.getLastTouchedShip();
        boolean recentContact = contactedShip != null
                && draggable.getTicksSinceTouchedShip()
                < VSConfig.ticksToStickToShip;

        SupportLease supportLease;
        if (recentContact && draggable.isStandingOnShip()) {
            supportLease = new SupportLease(contactedShip.getUuid(), 0);
        }
        else {
            boolean intentionalSeparation =
                    !entity.onGround
                            && entity.motionY > INTENTIONAL_SEPARATION_VELOCITY;
            boolean sameRecentShip = recentContact
                    && previousLease != null
                    && previousLease.shipUuid.equals(contactedShip.getUuid());
            if (intentionalSeparation
                    || !sameRecentShip
                    || previousLease.missedTicks >= CLIENT_SUPPORT_MISS_TICKS) {
                supportLease = null;
            }
            else {
                supportLease = new SupportLease(
                        previousLease.shipUuid,
                        previousLease.missedTicks + 1
                );
            }
        }

        if (supportLease == null) {
            clientPlayerRenderStates.remove(entity);
            return;
        }

        PhysicsObject ship = getShip(world, supportLease.shipUuid);
        if (ship == null) {
            clientPlayerRenderStates.remove(entity);
            return;
        }

        Vector3d observedLocal = new Vector3d(entity.posX, entity.posY, entity.posZ);
        ship.getShipTransformationManager()
                .getCurrentTickTransform()
                .transformPosition(
                        observedLocal,
                        TransformType.GLOBAL_TO_SUBSPACE
                );

        if (previousState != null && previousState.supportLease.shipUuid.equals(supportLease.shipUuid)) {
            clientPlayerRenderStates.put(
                    entity,
                    new ClientPlayerRenderState(
                            supportLease,
                            new Vector3d(previousState.currentLocalPosition),
                            observedLocal
                    )
            );
        }
        else clientPlayerRenderStates.put(entity, new ClientPlayerRenderState(supportLease, new Vector3d(observedLocal), observedLocal));
    }

    /**
     * Applies the newly advanced client ship transform to supported entity
     * positions and vanilla position targets only.
     */
    public static void applyClientEntityShipTick(@NotNull World world) {
        Iterator<Map.Entry<Entity, ShipLocalEntityRenderData>> remoteIterator =
                remoteRenderData.entrySet().iterator();
        while (remoteIterator.hasNext()) {
            Map.Entry<Entity, ShipLocalEntityRenderData> entry =
                    remoteIterator.next();
            Entity entity = entry.getKey();
            if (entity.world != world
                    || entity.isDead
                    || entry.getValue().isExpired(world)) {
                carrierYawRenderStates.remove(entity);
                remoteIterator.remove();
            }
        }

        Iterator<Map.Entry<Entity, ClientPlayerRenderState>> playerIterator =
                clientPlayerRenderStates.entrySet().iterator();
        while (playerIterator.hasNext()) {
            Map.Entry<Entity, ClientPlayerRenderState> entry =
                    playerIterator.next();
            Entity entity = entry.getKey();

            ClientPlayerRenderState state = entry.getValue();
            if (entity.world != world || entity.isDead || entity.isRiding()) {
                playerIterator.remove();
                continue;
            }

            PhysicsObject ship = getShip(world, state.supportLease.shipUuid);
            if (ship == null) {
                playerIterator.remove();
                continue;
            }

            setPositionIfSafe(
                    entity,
                    toWorld(
                            ship.getShipTransformationManager()
                                    .getCurrentTickTransform(),
                            state.currentLocalPosition
                    )
            );
        }

        for (Map.Entry<Entity, ShipLocalEntityRenderData> entry : remoteRenderData.entrySet()) {
            Entity entity = entry.getKey();
            if (entity.world != world || entity.isDead || entity.isRiding() || ValkyrienUtils.getMountedShipAndPos(entity).isMounted()) {
                continue;
            }

            PhysicsObject ship = getShip(world, entry.getValue().getShipUuid());
            if (ship == null) continue;

            ShipTransformationManager transforms = ship.getShipTransformationManager();
            ShipTransform previousTransform = transforms.getPrevTickTransform();
            ShipTransform currentTransform = transforms.getCurrentTickTransform();
            Matrix4d carrierTransform = ShipTransform.createTransform(
                    previousTransform,
                    currentTransform
            );
            Vector3d carriedPosition = carrierTransform.transformPosition(new Vector3d(entity.posX, entity.posY, entity.posZ));
            if (setPositionIfSafe(entity, carriedPosition) && entity instanceof EntityLivingBase living) {
                Vector3d transformedTarget = carrierTransform.transformPosition(
                        new Vector3d(
                                living.interpTargetX,
                                living.interpTargetY,
                                living.interpTargetZ
                        )
                );
                setPositionTarget(living, transformedTarget);
            }
        }
    }

    /**
     * Returns a render-only yaw offset that keeps a supported model in the
     * carrier's local orientation. Entity yaw state remains untouched.
     */
    public static float getCarrierRenderYawOffset(@NotNull Entity entity, @NotNull World world) {
        ShipLocalEntityRenderData renderData = remoteRenderData.get(entity);
        CarrierYawRenderState yawState = carrierYawRenderStates.get(entity);
        if (renderData == null || yawState == null || !yawState.shipUuid.equals(renderData.getShipUuid()) || renderData.isExpired(world)) {
            carrierYawRenderStates.remove(entity);
            return 0.0F;
        }

        PhysicsObject ship = getShip(world, yawState.shipUuid);
        if (ship == null) return 0.0F;

        Quaterniondc renderRotation = ship.getShipTransformationManager()
                .getRenderTransform()
                .rotationQuaternion(TransformType.SUBSPACE_TO_GLOBAL);
        Vector3d referenceForward = yawState.referenceRotation.transform(
                new Vector3d(0.0D, 0.0D, 1.0D)
        );
        Vector3d currentForward = renderRotation.transform(
                new Vector3d(0.0D, 0.0D, 1.0D)
        );
        double referenceYaw = Math.toDegrees(
                Math.atan2(-referenceForward.x, referenceForward.z)
        );
        double currentYaw = Math.toDegrees(
                Math.atan2(-currentForward.x, currentForward.z)
        );
        double wrappedYaw = (currentYaw - referenceYaw) % 360.0D;
        if (wrappedYaw >= 180.0D) wrappedYaw -= 360.0D;
        if (wrappedYaw < -180.0D) wrappedYaw += 360.0D;
        return (float) wrappedYaw;
    }

    /**
     * Normalizes the render-view entity into the world render frame.
     *
     * <p>This is required for a captain-chair camera: camera orientation already
     * includes the ship rotation, but RenderManager otherwise derives its world
     * origin from the rider's untransformed entity coordinates.</p>
     */
    public static boolean applyClientPlayerRenderPosition(
            Entity entity,
            World world,
            double partialTicks
    ) {
        EntityShipMountData mountData =
                ValkyrienUtils.getMountedShipAndPos(entity);
        if (mountData.isMounted() && mountData.mountPos() != null) {
            Vector3d renderedMountPosition = JOML.convert(
                    mountData.mountPos()
            );
            mountData.mountedShip()
                    .getShipTransformationManager()
                    .getRenderTransform()
                    .transformPosition(
                            renderedMountPosition,
                            TransformType.SUBSPACE_TO_GLOBAL
                    );
            backupEntityRenderPosition(entity);
            setEntityRenderPosition(entity, renderedMountPosition);
            return true;
        }

        ClientPlayerRenderState state =
                clientPlayerRenderStates.get(entity);
        if (state == null) return false;

        PhysicsObject ship = getShip(
                world,
                state.supportLease.shipUuid
        );
        if (ship == null) {
            clientPlayerRenderStates.remove(entity);
            return false;
        }

        Vector3d renderPosition = state.previousLocalPosition.lerp(
                state.currentLocalPosition,
                Math.clamp(partialTicks, 0.0D, 1.0D),
                new Vector3d()
        );
        ship.getShipTransformationManager()
                .getRenderTransform()
                .transformPosition(
                        renderPosition,
                        TransformType.SUBSPACE_TO_GLOBAL
                );
        backupEntityRenderPosition(entity);
        setEntityRenderPosition(entity, renderPosition);
        return true;
    }

    private static void backupEntityRenderPosition(Entity entity) {
        renderPositionBackups.computeIfAbsent(
                entity,
                ignored -> new EntityRenderPositionBackup(
                        entity.posX,
                        entity.posY,
                        entity.posZ,
                        entity.lastTickPosX,
                        entity.lastTickPosY,
                        entity.lastTickPosZ,
                        entity.prevPosX,
                        entity.prevPosY,
                        entity.prevPosZ,
                        entity.getEntityBoundingBox()
                )
        );
    }

    private static void setEntityRenderPosition(
            Entity entity,
            Vector3dc position
    ) {
        final double deltaX = position.x() - entity.posX;
        final double deltaY = position.y() - entity.posY;
        final double deltaZ = position.z() - entity.posZ;

        entity.posX = position.x();
        entity.posY = position.y();
        entity.posZ = position.z();
        entity.lastTickPosX = position.x();
        entity.lastTickPosY = position.y();
        entity.lastTickPosZ = position.z();
        entity.prevPosX = position.x();
        entity.prevPosY = position.y();
        entity.prevPosZ = position.z();
        entity.setEntityBoundingBox(
                entity.getEntityBoundingBox().offset(deltaX, deltaY, deltaZ)
        );
    }

    private static boolean setPositionIfSafe(
            Entity entity,
            Vector3dc position
    ) {
        if (!position.isFinite()) return false;

        double deltaX = position.x() - entity.posX;
        double deltaY = position.y() - entity.posY;
        double deltaZ = position.z() - entity.posZ;
        if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ
                > MAX_CLIENT_CARRIER_STEP_SQUARED) {
            return false;
        }
        entity.setPosition(position.x(), position.y(), position.z());
        return true;
    }

    private static void setPositionTarget(
            EntityLivingBase entity,
            Vector3dc target
    ) {
        entity.interpTargetX = target.x();
        entity.interpTargetY = target.y();
        entity.interpTargetZ = target.z();
    }

    @Nullable
    private static PhysicsObject getShip(
            World world,
            UUID shipUuid
    ) {
        IPhysObjectWorld physObjectWorld = ValkyrienUtils.getPhysObjWorld(world);
        return physObjectWorld == null
                ? null
                : physObjectWorld.getPhysObjectFromUUID(shipUuid);
    }

    private static Vector3d toWorld(
            ShipTransform shipTransform,
            Vector3dc localPosition
    ) {
        Vector3d worldPosition = new Vector3d(localPosition);
        shipTransform.transformPosition(
                worldPosition,
                TransformType.SUBSPACE_TO_GLOBAL
        );
        return worldPosition;
    }

    private record SupportLease(UUID shipUuid, int missedTicks) {
    }

    private record ClientPlayerRenderState(
            SupportLease supportLease,
            Vector3dc previousLocalPosition,
            Vector3dc currentLocalPosition
    ) {
    }

    private record CarrierYawRenderState(
            UUID shipUuid,
            Quaterniondc referenceRotation
    ) {
    }
}
