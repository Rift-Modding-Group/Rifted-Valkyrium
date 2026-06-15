package org.valkyrienskies.mod.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IThreadListener;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.valkyrienskies.mod.common.piloting.ITileEntityPilotable;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

import java.util.UUID;

public class MessagePlayerStoppedPiloting implements IMessage {
    public BlockPos posToStopPiloting;
    public UUID shipIDToStopPiloting;

    public MessagePlayerStoppedPiloting(BlockPos posToStopPiloting) {
        this.posToStopPiloting = posToStopPiloting;
        this.shipIDToStopPiloting = null;
    }

    public MessagePlayerStoppedPiloting(UUID shipIDToStopPiloting) {
        this.posToStopPiloting = null;
        this.shipIDToStopPiloting = shipIDToStopPiloting;
    }

    public MessagePlayerStoppedPiloting() {}

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuf = new PacketBuffer(buf);
        final boolean isBlockPos = packetBuf.readBoolean();
        final boolean isUUID = packetBuf.readBoolean();

        if (isBlockPos) {
            posToStopPiloting = new BlockPos(
                    packetBuf.readInt(),
                    packetBuf.readInt(),
                    packetBuf.readInt()
            );
        }
        if (isUUID) {
            shipIDToStopPiloting = packetBuf.readUniqueId();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packetBuf = new PacketBuffer(buf);

        packetBuf.writeBoolean(posToStopPiloting != null);
        packetBuf.writeBoolean(shipIDToStopPiloting != null);

        if (posToStopPiloting != null) {
            packetBuf.writeInt(posToStopPiloting.getX());
            packetBuf.writeInt(posToStopPiloting.getY());
            packetBuf.writeInt(posToStopPiloting.getZ());
        }

        if (shipIDToStopPiloting != null) {
            packetBuf.writeUniqueId(shipIDToStopPiloting);
        }
        //use absolute coordinates instead of writeBlockPos in case we ever add compatibility with cubic chunks
    }

    public static class Handler implements IMessageHandler<MessagePlayerStoppedPiloting, IMessage> {
        @Override
        public IMessage onMessage(MessagePlayerStoppedPiloting message, MessageContext ctx) {
            IThreadListener mainThread = ctx.getServerHandler().player.server;
            mainThread.addScheduledTask(() -> {
                EntityPlayerMP player = ctx.getServerHandler().player;
                //used when position is basis of piloting
                if (message.posToStopPiloting != null) {
                    BlockPos pos = message.posToStopPiloting;

                    TileEntity tileEntity = player.world.getTileEntity(pos);

                    if (tileEntity instanceof ITileEntityPilotable tileEntityPilotable) {
                        tileEntityPilotable.playerWantsToStopPiloting(player);
                    }
                }
                //used when ship is basis of piloting
                else {
                    final UUID shipID = message.shipIDToStopPiloting;
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
