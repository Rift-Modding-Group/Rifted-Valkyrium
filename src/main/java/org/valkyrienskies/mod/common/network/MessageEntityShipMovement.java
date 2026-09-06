package org.valkyrienskies.mod.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.IThreadListener;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.api.TransformType;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.anchored_mount.IShipAnchoredMount;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.ShipLocalEntityMovementData;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.ships.QueryableShipData;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

import java.util.Optional;
import java.util.UUID;

/**
 * Synchronizes a remote entity in ship coordinates instead of giving vanilla a stale world-space
 * interpolation target. A zero component mask ends ship-relative movement for the entity.
 */
public class MessageEntityShipMovement implements IMessage {
    public static final int POSITION = 1;
    public static final int ROTATION = 1 << 1;
    public static final int VELOCITY = 1 << 2;
    public static final int HEAD_ROTATION = 1 << 3;
    public static final int ALL_COMPONENTS = POSITION | ROTATION | VELOCITY | HEAD_ROTATION;

    private int entityId;
    private int componentMask;
    private UUID shipUuid;
    private double localX;
    private double localY;
    private double localZ;
    private double localVelocityX;
    private double localVelocityY;
    private double localVelocityZ;
    private double localYaw;
    private double localHeadYaw;
    private float pitch;
    private boolean onGround;
    private long serverPosX;
    private long serverPosY;
    private long serverPosZ;

    public MessageEntityShipMovement() {}

    public MessageEntityShipMovement(int entityId) {
        this.entityId = entityId;
    }

    public MessageEntityShipMovement(
            int entityId,
            int componentMask,
            @NotNull UUID shipUuid,
            @NotNull Vector3dc localPosition,
            @NotNull Vector3dc localVelocity,
            double localYaw,
            float pitch,
            double localHeadYaw,
            boolean onGround,
            long serverPosX,
            long serverPosY,
            long serverPosZ
    ) {
        this.entityId = entityId;
        this.componentMask = componentMask;
        this.shipUuid = shipUuid;
        this.localX = localPosition.x();
        this.localY = localPosition.y();
        this.localZ = localPosition.z();
        this.localVelocityX = localVelocity.x();
        this.localVelocityY = localVelocity.y();
        this.localVelocityZ = localVelocity.z();
        this.localYaw = localYaw;
        this.pitch = pitch;
        this.localHeadYaw = localHeadYaw;
        this.onGround = onGround;
        this.serverPosX = serverPosX;
        this.serverPosY = serverPosY;
        this.serverPosZ = serverPosZ;
    }

    public MessageEntityShipMovement(
            @NotNull Entity entity,
            @NotNull ShipData shipData,
            int componentMask,
            long serverPosX,
            long serverPosY,
            long serverPosZ
    ) {
        this.entityId = entity.getEntityId();
        this.componentMask = componentMask;
        this.shipUuid = shipData.getUuid();
        this.onGround = entity.onGround;
        this.serverPosX = serverPosX;
        this.serverPosY = serverPosY;
        this.serverPosZ = serverPosZ;

        ShipTransform shipTransform = shipData.getShipTransform();
        if (this.hasComponent(POSITION)) {
            Vector3d localPosition = new Vector3d(entity.posX, entity.posY, entity.posZ);
            shipTransform.transformPosition(localPosition, TransformType.GLOBAL_TO_SUBSPACE);
            this.localX = localPosition.x;
            this.localY = localPosition.y;
            this.localZ = localPosition.z;
        }
        if (this.hasComponent(VELOCITY)) {
            Vector3d localVelocity = new Vector3d(entity.motionX, entity.motionY, entity.motionZ);
            shipTransform.transformDirection(localVelocity, TransformType.GLOBAL_TO_SUBSPACE);
            this.localVelocityX = localVelocity.x;
            this.localVelocityY = localVelocity.y;
            this.localVelocityZ = localVelocity.z;
        }
        if (this.hasComponent(ROTATION)) {
            this.localYaw = ShipLocalEntityMovementData.transformYaw(
                    shipTransform,
                    entity.rotationYaw,
                    TransformType.GLOBAL_TO_SUBSPACE
            );
            this.pitch = entity.rotationPitch;
        }
        if (this.hasComponent(HEAD_ROTATION)) {
            this.localHeadYaw = ShipLocalEntityMovementData.transformYaw(
                    shipTransform,
                    entity.getRotationYawHead(),
                    TransformType.GLOBAL_TO_SUBSPACE
            );
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        PacketBuffer packetBuffer = new PacketBuffer(buffer);
        this.entityId = packetBuffer.readInt();
        this.componentMask = packetBuffer.readUnsignedByte();
        if (this.componentMask == 0) return;

        this.shipUuid = packetBuffer.readUniqueId();
        this.onGround = packetBuffer.readBoolean();
        if (this.hasComponent(POSITION)) {
            this.localX = packetBuffer.readDouble();
            this.localY = packetBuffer.readDouble();
            this.localZ = packetBuffer.readDouble();
            this.serverPosX = packetBuffer.readLong();
            this.serverPosY = packetBuffer.readLong();
            this.serverPosZ = packetBuffer.readLong();
        }
        if (this.hasComponent(VELOCITY)) {
            this.localVelocityX = packetBuffer.readDouble();
            this.localVelocityY = packetBuffer.readDouble();
            this.localVelocityZ = packetBuffer.readDouble();
        }
        if (this.hasComponent(ROTATION)) {
            this.localYaw = packetBuffer.readDouble();
            this.pitch = packetBuffer.readFloat();
        }
        if (this.hasComponent(HEAD_ROTATION)) {
            this.localHeadYaw = packetBuffer.readDouble();
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        PacketBuffer packetBuffer = new PacketBuffer(buffer);
        packetBuffer.writeInt(this.entityId);
        packetBuffer.writeByte(this.componentMask);
        if (this.componentMask == 0) return;

        packetBuffer.writeUniqueId(this.shipUuid);
        packetBuffer.writeBoolean(this.onGround);
        if (this.hasComponent(POSITION)) {
            packetBuffer.writeDouble(this.localX);
            packetBuffer.writeDouble(this.localY);
            packetBuffer.writeDouble(this.localZ);
            packetBuffer.writeLong(this.serverPosX);
            packetBuffer.writeLong(this.serverPosY);
            packetBuffer.writeLong(this.serverPosZ);
        }
        if (this.hasComponent(VELOCITY)) {
            packetBuffer.writeDouble(this.localVelocityX);
            packetBuffer.writeDouble(this.localVelocityY);
            packetBuffer.writeDouble(this.localVelocityZ);
        }
        if (this.hasComponent(ROTATION)) {
            packetBuffer.writeDouble(this.localYaw);
            packetBuffer.writeFloat(this.pitch);
        }
        if (this.hasComponent(HEAD_ROTATION)) {
            packetBuffer.writeDouble(this.localHeadYaw);
        }
    }

    public int getEntityId() {
        return this.entityId;
    }

    public int getComponentMask() {
        return this.componentMask;
    }

    public UUID getShipUuid() {
        return this.shipUuid;
    }

    public double getLocalX() {
        return this.localX;
    }

    public double getLocalY() {
        return this.localY;
    }

    public double getLocalZ() {
        return this.localZ;
    }

    public double getLocalVelocityX() {
        return this.localVelocityX;
    }

    public double getLocalVelocityY() {
        return this.localVelocityY;
    }

    public double getLocalVelocityZ() {
        return this.localVelocityZ;
    }

    public double getLocalYaw() {
        return this.localYaw;
    }

    public double getLocalHeadYaw() {
        return this.localHeadYaw;
    }

    public float getPitch() {
        return this.pitch;
    }

    public boolean isOnGround() {
        return this.onGround;
    }

    public long getServerPosX() {
        return this.serverPosX;
    }

    public long getServerPosY() {
        return this.serverPosY;
    }

    public long getServerPosZ() {
        return this.serverPosZ;
    }

    private boolean hasComponent(int component) {
        return (this.componentMask & component) != 0;
    }

    public static class Handler implements IMessageHandler<MessageEntityShipMovement, IMessage> {
        @Override
        @SuppressWarnings("Convert2Lambda")
        public IMessage onMessage(MessageEntityShipMovement message, MessageContext context) {
            IThreadListener mainThread = Minecraft.getMinecraft();
            mainThread.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    World world = Minecraft.getMinecraft().world;
                    if (world == null) return;

                    Entity entity = world.getEntityByID(message.entityId);
                    if (entity == null) return;

                    IEntityShipDraggable draggable = entity.getCapability(
                            VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE,
                            null
                    );
                    if (draggable == null) return;

                    ShipLocalEntityMovementData movementData = draggable.getShipLocalMovementData();
                    if (message.componentMask == 0) {
                        if (movementData != null) movementData.clear();
                        draggable.setLastTouchedShip(null);
                        return;
                    }

                    IPhysObjectWorld physObjectWorld = ValkyrienUtils.getPhysObjWorld(world);
                    PhysicsObject physicsObject = physObjectWorld == null
                            ? null : physObjectWorld.getPhysObjectFromUUID(message.shipUuid);
                    Optional<ShipData> indexedShip = QueryableShipData.get(world).getShip(message.shipUuid);
                    ShipTransform shipTransform = physicsObject == null
                            ? indexedShip.map(ShipData::getShipTransform).orElse(null)
                            : physicsObject.getShipTransformationManager().getCurrentTickTransform();

                    if (message.hasComponent(POSITION)) {
                        ResourceLocation entityType = EntityList.getKey(entity);
                        if (entityType != null && VSConfig.sittableBlockEntityIDsSet != null
                                && VSConfig.sittableBlockEntityIDsSet.contains(entityType)) {
                            IShipAnchoredMount anchoredMount = entity.getCapability(
                                    VSCapabilityRegistry.VS_SHIP_ANCHORED_MOUNT,
                                    null
                            );
                            if (anchoredMount != null) {
                                anchoredMount.setAnchorMountData(
                                        new Vec3d(message.localX, message.localY, message.localZ),
                                        new BlockPos(message.localX, message.localY, message.localZ)
                                );
                            }
                        }
                    }

                    Minecraft minecraft = Minecraft.getMinecraft();
                    if (entity == minecraft.player || entity.canPassengerSteer()) {
                        if (movementData != null) movementData.clear();
                        if (message.hasComponent(VELOCITY) && shipTransform != null) {
                            Vector3d worldVelocity = new Vector3d(
                                    message.localVelocityX,
                                    message.localVelocityY,
                                    message.localVelocityZ
                            );
                            shipTransform.transformDirection(worldVelocity, TransformType.SUBSPACE_TO_GLOBAL);
                            entity.setVelocity(worldVelocity.x, worldVelocity.y, worldVelocity.z);
                        }
                        return;
                    }

                    movementData = draggable.getOrCreateShipLocalMovementData();
                    movementData.useShip(message.shipUuid);

                    if (shipTransform != null) {
                        movementData.initializeIfNeeded(
                                shipTransform,
                                new Vector3d(entity.posX, entity.posY, entity.posZ),
                                entity.rotationYaw,
                                entity.rotationPitch,
                                entity.getRotationYawHead()
                        );
                    }
                    if (message.hasComponent(POSITION)) {
                        movementData.setPositionTarget(
                                new Vector3d(message.localX, message.localY, message.localZ),
                                ShipLocalEntityMovementData.DEFAULT_LERP_STEPS
                        );
                        entity.serverPosX = message.serverPosX;
                        entity.serverPosY = message.serverPosY;
                        entity.serverPosZ = message.serverPosZ;
                    }
                    if (message.hasComponent(VELOCITY)) {
                        movementData.setVelocity(new Vector3d(
                                message.localVelocityX,
                                message.localVelocityY,
                                message.localVelocityZ
                        ));
                    }
                    if (message.hasComponent(ROTATION)) {
                        movementData.setRotationTarget(
                                message.localYaw,
                                message.pitch,
                                ShipLocalEntityMovementData.DEFAULT_LERP_STEPS
                        );
                    }
                    if (message.hasComponent(HEAD_ROTATION)) {
                        movementData.setHeadRotationTarget(
                                message.localHeadYaw,
                                ShipLocalEntityMovementData.DEFAULT_LERP_STEPS
                        );
                    }
                    movementData.setOnGround(message.onGround);

                    if (physicsObject != null) {
                        draggable.setLastTouchedShip(physicsObject.getShipData());
                        draggable.setTicksSinceTouchedShip(0);
                    }
                }
            });
            return null;
        }
    }
}
