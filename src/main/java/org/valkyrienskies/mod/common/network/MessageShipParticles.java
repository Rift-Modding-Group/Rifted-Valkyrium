package org.valkyrienskies.mod.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.IThreadListener;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.valkyrienskies.mod.client.particle.ShipParticleSpawner;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.ships.ship_world.ShipParticleResolver.ResolvedShip;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

import java.util.UUID;

/**
 * Transmits server particles in a stable ship-local frame.
 * */
public class MessageShipParticles implements IMessage {
    private UUID shipUuid;
    private int particleId;
    private boolean longDistance;
    private boolean directionsLocal;
    private double directionRotationX;
    private double directionRotationY;
    private double directionRotationZ;
    private double directionRotationW;
    private double localX;
    private double localY;
    private double localZ;
    private int count;
    private double xOffset;
    private double yOffset;
    private double zOffset;
    private double particleSpeed;
    private int[] parameters;

    public MessageShipParticles() {}

    public MessageShipParticles(
            @NotNull ResolvedShip resolvedShip, @NotNull EnumParticleTypes particleType, boolean longDistance,
            double x, double y, double z, int count,
            double xOffset, double yOffset, double zOffset,
            double particleSpeed, int... parameters
    ) {
        this.shipUuid = resolvedShip.ship().getUuid();
        this.particleId = particleType.getParticleID();
        this.longDistance = longDistance;
        this.directionsLocal = resolvedShip.localCoordinates();

        Vector3d localPosition = new Vector3d(x, y, z);
        if (!resolvedShip.localCoordinates()) {
            Quaterniond directionRotation = resolvedShip.ship().getShipTransformationManager()
                    .getCurrentTickTransform()
                    .rotationQuaternion(TransformType.GLOBAL_TO_SUBSPACE);
            this.directionRotationX = directionRotation.x;
            this.directionRotationY = directionRotation.y;
            this.directionRotationZ = directionRotation.z;
            this.directionRotationW = directionRotation.w;
            resolvedShip.ship().getShipTransformationManager().getCurrentTickTransform()
                    .transformPosition(localPosition, TransformType.GLOBAL_TO_SUBSPACE);
        }
        this.localX = localPosition.x;
        this.localY = localPosition.y;
        this.localZ = localPosition.z;
        this.count = count;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.zOffset = zOffset;
        this.particleSpeed = particleSpeed;
        this.parameters = parameters.clone();
    }

    @Override
    public void fromBytes(@NotNull ByteBuf buffer) {
        PacketBuffer packetBuffer = new PacketBuffer(buffer);
        this.shipUuid = packetBuffer.readUniqueId();
        this.particleId = packetBuffer.readVarInt();
        this.longDistance = packetBuffer.readBoolean();
        this.directionsLocal = packetBuffer.readBoolean();
        if (!this.directionsLocal) {
            this.directionRotationX = packetBuffer.readDouble();
            this.directionRotationY = packetBuffer.readDouble();
            this.directionRotationZ = packetBuffer.readDouble();
            this.directionRotationW = packetBuffer.readDouble();
        }
        this.localX = packetBuffer.readDouble();
        this.localY = packetBuffer.readDouble();
        this.localZ = packetBuffer.readDouble();
        this.count = packetBuffer.readInt();
        this.xOffset = packetBuffer.readDouble();
        this.yOffset = packetBuffer.readDouble();
        this.zOffset = packetBuffer.readDouble();
        this.particleSpeed = packetBuffer.readDouble();
        int parameterCount = packetBuffer.readVarInt();
        this.parameters = new int[parameterCount];
        for (int index = 0; index < parameterCount; index++) {
            this.parameters[index] = packetBuffer.readVarInt();
        }
    }

    @Override
    public void toBytes(@NotNull ByteBuf buffer) {
        PacketBuffer packetBuffer = new PacketBuffer(buffer);
        packetBuffer.writeUniqueId(this.shipUuid);
        packetBuffer.writeVarInt(this.particleId);
        packetBuffer.writeBoolean(this.longDistance);
        packetBuffer.writeBoolean(this.directionsLocal);
        if (!this.directionsLocal) {
            packetBuffer.writeDouble(this.directionRotationX);
            packetBuffer.writeDouble(this.directionRotationY);
            packetBuffer.writeDouble(this.directionRotationZ);
            packetBuffer.writeDouble(this.directionRotationW);
        }
        packetBuffer.writeDouble(this.localX);
        packetBuffer.writeDouble(this.localY);
        packetBuffer.writeDouble(this.localZ);
        packetBuffer.writeInt(this.count);
        packetBuffer.writeDouble(this.xOffset);
        packetBuffer.writeDouble(this.yOffset);
        packetBuffer.writeDouble(this.zOffset);
        packetBuffer.writeDouble(this.particleSpeed);
        packetBuffer.writeVarInt(this.parameters.length);
        for (int parameter : this.parameters) packetBuffer.writeVarInt(parameter);
    }

    public static class Handler implements IMessageHandler<MessageShipParticles, IMessage> {
        @Override
        public IMessage onMessage(@NotNull MessageShipParticles message, @NotNull MessageContext context) {
            IThreadListener mainThread = Minecraft.getMinecraft();
            mainThread.addScheduledTask(() -> spawnParticles(message));
            return null;
        }

        private static void spawnParticles(@NotNull MessageShipParticles message) {
            WorldClient world = Minecraft.getMinecraft().world;
            if (world == null) return;

            IPhysObjectWorld physObjectWorld = ValkyrienUtils.getPhysObjWorld(world);
            PhysicsObject ship = physObjectWorld == null
                    ? null : physObjectWorld.getPhysObjectFromUUID(message.shipUuid);
            EnumParticleTypes particleType = EnumParticleTypes.getParticleFromId(message.particleId);
            if (ship == null || particleType == null) return;

            boolean ignoreRange = message.longDistance || particleType.getShouldIgnoreRange();
            Quaterniond directionRotation;
            if (message.directionsLocal) directionRotation = null;
            else {
                directionRotation = new Quaterniond(
                        message.directionRotationX,
                        message.directionRotationY,
                        message.directionRotationZ,
                        message.directionRotationW
                );
            }
            if (message.count == 0) {
                Vector3d velocity = new Vector3d(
                        message.xOffset * message.particleSpeed,
                        message.yOffset * message.particleSpeed,
                        message.zOffset * message.particleSpeed
                );
                transformDirectionIfNeeded(message, directionRotation, velocity, true);
                spawnLocal(message, world, ship, ignoreRange, message.localX, message.localY, message.localZ, velocity);
                return;
            }

            for (int index = 0; index < message.count; index++) {
                Vector3d offset = new Vector3d(
                        world.rand.nextGaussian() * message.xOffset,
                        world.rand.nextGaussian() * message.yOffset,
                        world.rand.nextGaussian() * message.zOffset
                );
                Vector3d velocity = new Vector3d(
                        world.rand.nextGaussian() * message.particleSpeed,
                        world.rand.nextGaussian() * message.particleSpeed,
                        world.rand.nextGaussian() * message.particleSpeed
                );
                transformDirectionIfNeeded(message, directionRotation, offset, false);
                transformDirectionIfNeeded(message, directionRotation, velocity, true);
                spawnLocal(
                        message, world, ship, ignoreRange,
                        message.localX + offset.x,
                        message.localY + offset.y,
                        message.localZ + offset.z,
                        velocity
                );
            }
        }

        private static void transformDirectionIfNeeded(
                @NotNull MessageShipParticles message,
                @Nullable Quaterniond directionRotation,
                @NotNull Vector3d direction,
                boolean particleVelocity
        ) {
            if (directionRotation == null) return;
            if (particleVelocity && !ShipParticleSpawner.shouldTransformVelocity(message.particleId)) return;

            directionRotation.transform(direction);
        }

        private static void spawnLocal(
                @NotNull MessageShipParticles message, @NotNull WorldClient world,
                @NotNull PhysicsObject ship, boolean ignoreRange,
                double x, double y, double z, @NotNull Vector3d velocity
        ) {
            ShipParticleSpawner.spawnLocal(
                    world, ship, message.particleId, ignoreRange,
                    x, y, z, velocity.x, velocity.y, velocity.z, message.parameters
            );
        }
    }
}
