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
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.client.entity_position.EntityRenderPositionManager;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.anchored_mount.IShipAnchoredMount;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.ships.ShipData;

import java.util.UUID;

/**
 * Render-only ship-local position data for entities standing on ships.
 * Lets clients avoid vanilla's delayed global interpolation.
 */
public class MessageEntityShipRenderPosition implements IMessage {
    private int entityId;
    private UUID shipUuid;
    private double localX;
    private double localY;
    private double localZ;

    public MessageEntityShipRenderPosition() {}

    public MessageEntityShipRenderPosition(Entity entity, ShipData shipData, Vector3dc localPosition) {
        this.entityId = entity.getEntityId();
        this.shipUuid = shipData.getUuid();
        this.localX = localPosition.x();
        this.localY = localPosition.y();
        this.localZ = localPosition.z();
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        final PacketBuffer packetBuffer = new PacketBuffer(buf);
        this.entityId = packetBuffer.readInt();
        this.shipUuid = packetBuffer.readUniqueId();
        this.localX = packetBuffer.readDouble();
        this.localY = packetBuffer.readDouble();
        this.localZ = packetBuffer.readDouble();
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        final PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeInt(this.entityId);
        packetBuffer.writeUniqueId(this.shipUuid);
        packetBuffer.writeDouble(this.localX);
        packetBuffer.writeDouble(this.localY);
        packetBuffer.writeDouble(this.localZ);
    }

    public static class Handler implements IMessageHandler<MessageEntityShipRenderPosition, IMessage> {
        @Override
        public IMessage onMessage(final MessageEntityShipRenderPosition message, final MessageContext ctx) {
            final IThreadListener mainThread = Minecraft.getMinecraft();
            mainThread.addScheduledTask(() -> {
                World world = Minecraft.getMinecraft().world;
                if (world == null) return;

                Entity entity = world.getEntityByID(message.entityId);
                if (entity == null || entity == Minecraft.getMinecraft().player) return;

                long updateTick = world.getTotalWorldTime();
                Vector3dc localPosition = new Vector3d(message.localX, message.localY, message.localZ);
                ResourceLocation entityId = EntityList.getKey(entity);
                if (entityId != null && VSConfig.sittableBlockEntityIDsSet != null && VSConfig.sittableBlockEntityIDsSet.contains(entityId)) {
                    IShipAnchoredMount anchoredMount = entity.getCapability(VSCapabilityRegistry.VS_SHIP_ANCHORED_MOUNT, null);
                    if (anchoredMount != null) {
                        anchoredMount.setAnchorMountData(
                                new Vec3d(message.localX, message.localY, message.localZ),
                                new BlockPos(message.localX, message.localY, message.localZ)
                        );
                    }
                }

                EntityRenderPositionManager.queueShipLocalRenderData(
                        entity,
                        message.shipUuid,
                        localPosition,
                        updateTick
                );
            });

            return null;
        }
    }
}
