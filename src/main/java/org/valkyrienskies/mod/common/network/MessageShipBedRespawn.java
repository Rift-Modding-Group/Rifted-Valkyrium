package org.valkyrienskies.mod.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import valkyrienwarfare.api.TransformType;

import java.util.UUID;

/**
 * Precedes the vanilla respawn teleport so it can be placed in the client's ship frame.
 */
public class MessageShipBedRespawn implements IMessage {
    private int dimension;
    private UUID shipId;
    private Vector3d localPosition;

    public MessageShipBedRespawn() {}

    public MessageShipBedRespawn(int dimension, @NotNull UUID shipId, @NotNull Vector3dc worldPosition, @NotNull ShipTransform transform) {
        this.dimension = dimension;
        this.shipId = shipId;
        this.localPosition = new Vector3d(worldPosition);
        transform.transformPosition(this.localPosition, TransformType.GLOBAL_TO_SUBSPACE);
    }

    @Override
    public void fromBytes(@NotNull ByteBuf buffer) {
        PacketBuffer packetBuffer = new PacketBuffer(buffer);
        this.dimension = packetBuffer.readInt();
        this.shipId = packetBuffer.readUniqueId();
        this.localPosition = new Vector3d(packetBuffer.readDouble(), packetBuffer.readDouble(), packetBuffer.readDouble());
    }

    @Override
    public void toBytes(@NotNull ByteBuf buffer) {
        PacketBuffer packetBuffer = new PacketBuffer(buffer);
        packetBuffer.writeInt(this.dimension);
        packetBuffer.writeUniqueId(this.shipId);
        packetBuffer.writeDouble(this.localPosition.x);
        packetBuffer.writeDouble(this.localPosition.y);
        packetBuffer.writeDouble(this.localPosition.z);
    }

    public static class Handler implements IMessageHandler<MessageShipBedRespawn, IMessage> {
        @Override
        @Nullable
        @SuppressWarnings("Convert2Lambda")
        public IMessage onMessage(@NotNull MessageShipBedRespawn message, @NotNull MessageContext context) {
            Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    EntityPlayerSP player = Minecraft.getMinecraft().player;
                    if (player == null || player.dimension != message.dimension) return;

                    IEntityShipDraggable draggable = player.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
                    if (draggable != null) {
                        draggable.setPendingShipPosition(message.shipId, message.localPosition, true);
                    }
                }
            });
            return null;
        }
    }
}
