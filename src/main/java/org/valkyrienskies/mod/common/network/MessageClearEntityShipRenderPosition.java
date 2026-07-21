package org.valkyrienskies.mod.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.IThreadListener;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.valkyrienskies.mod.client.EventsClient;

/**
 * Clears client-side ship-local render data for an entity that is no longer ship-bound.
 */
public class MessageClearEntityShipRenderPosition implements IMessage {
    private int entityId;

    public MessageClearEntityShipRenderPosition() {}

    public MessageClearEntityShipRenderPosition(final Entity entity) {
        this.entityId = entity.getEntityId();
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        this.entityId = new PacketBuffer(buf).readInt();
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        new PacketBuffer(buf).writeInt(this.entityId);
    }

    public static class Handler implements IMessageHandler<MessageClearEntityShipRenderPosition, IMessage> {
        @Override
        @SuppressWarnings("Convert2Lambda")
        public IMessage onMessage(final MessageClearEntityShipRenderPosition message, final MessageContext ctx) {
            final IThreadListener mainThread = Minecraft.getMinecraft();
            mainThread.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    final World world = Minecraft.getMinecraft().world;
                    if (world == null) return;

                    final Entity entity = world.getEntityByID(message.entityId);
                    if (entity != null && !(entity instanceof EntityPlayer)) {
                        EventsClient.shipLocalEntityRenderData.remove(entity);
                    }
                }
            });

            return null;
        }
    }
}
