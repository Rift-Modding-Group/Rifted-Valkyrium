package org.valkyrienskies.addon.control.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IThreadListener;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.valkyrienskies.addon.control.tileentity.ITileEntityControlNode;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

import java.util.UUID;

public class VSStoppedUsingControlNodeMessage implements IMessage {
    public BlockPos posToStopUsing;
    public UUID shipIDToStopUsing;

    public VSStoppedUsingControlNodeMessage() {}

    public VSStoppedUsingControlNodeMessage(BlockPos posToStopUsing) {
        this.posToStopUsing = posToStopUsing;
        this.shipIDToStopUsing = null;
    }

    public VSStoppedUsingControlNodeMessage(UUID shipIDToStopUsing) {
        this.posToStopUsing = null;
        this.shipIDToStopUsing = shipIDToStopUsing;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuf = new PacketBuffer(buf);
        final boolean isBlockPos = packetBuf.readBoolean();
        final boolean isUUID = packetBuf.readBoolean();

        if (isBlockPos) this.posToStopUsing = packetBuf.readBlockPos();
        if (isUUID) this.shipIDToStopUsing = packetBuf.readUniqueId();

    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packetBuf = new PacketBuffer(buf);

        packetBuf.writeBoolean(this.posToStopUsing != null);
        packetBuf.writeBoolean(this.shipIDToStopUsing != null);

        if (this.posToStopUsing != null) {
            packetBuf.writeBlockPos(this.posToStopUsing);
        }

        if (this.shipIDToStopUsing != null) {
            packetBuf.writeUniqueId(this.shipIDToStopUsing);
        }
    }

    public static class Handler implements IMessageHandler<VSStoppedUsingControlNodeMessage, IMessage> {
        @Override
        public IMessage onMessage(VSStoppedUsingControlNodeMessage message, MessageContext ctx) {
            IThreadListener mainThread = ctx.getServerHandler().server;
            mainThread.addScheduledTask(() -> {
                EntityPlayerMP player = ctx.getServerHandler().player;
                if (message.posToStopUsing != null) {
                    BlockPos pos = message.posToStopUsing;

                    TileEntity tileEntity = player.world.getTileEntity(pos);

                    if (tileEntity instanceof ITileEntityControlNode tileEntityControlNode) {
                        tileEntityControlNode.playerWantsToStopUsing(player);
                    }
                }
                else {
                    final UUID shipID = message.shipIDToStopUsing;
                    final IPhysObjectWorld physObjectWorld = ValkyrienUtils.getPhysObjWorld(player.world);
                    if (physObjectWorld == null) return;
                    final PhysicsObject physicsObject = physObjectWorld.getPhysObjectFromUUID(shipID);
                    if (physicsObject != null
                            && physicsObject.getShipPilot() != null
                            && player.getUniqueID().equals(physicsObject.getShipPilot().getPilot())
                    ) {
                        physicsObject.setShipPilot(null);
                    }
                }
            });
            return null;
        }
    }
}
