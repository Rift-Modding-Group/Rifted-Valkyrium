package org.valkyrienskies.mod.common.piloting;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IThreadListener;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.UUID;

public class PilotControlsMessage implements IMessage {
    private UUID pilotedShip;
    private BlockPos controllerPos;
    private int usedControls;

    public PilotControlsMessage() {}

    public PilotControlsMessage(UUID pilotedShip, BlockPos controllerPos) {
        this.pilotedShip = pilotedShip;
        this.controllerPos = controllerPos;
        this.usedControls = PilotControls.getUsedControls();
    }

    public int getUsedControls() {
        return this.usedControls;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuf = new PacketBuffer(buf);

        this.pilotedShip = packetBuf.readUniqueId();
        this.controllerPos = packetBuf.readBlockPos();
        this.usedControls = packetBuf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packetBuf = new PacketBuffer(buf);

        packetBuf.writeUniqueId(this.pilotedShip);
        packetBuf.writeBlockPos(this.controllerPos);
        packetBuf.writeInt(this.usedControls);
    }

    public static class Handler implements IMessageHandler<PilotControlsMessage, IMessage> {
        @Override
        public IMessage onMessage(PilotControlsMessage message, MessageContext ctx) {
            IThreadListener mainThread = ctx.getServerHandler().server;
            mainThread.addScheduledTask(() -> {
                if (message.controllerPos == null) return;

                World worldObj = ctx.getServerHandler().player.world;
                BlockPos posFor = message.controllerPos;
                TileEntity tile = worldObj.getTileEntity(posFor);

                if (!(tile instanceof ITileEntityPilotable pilotable)) return;
                pilotable.onPilotControlsMessage(message, ctx.getServerHandler().player);
            });
            return null;
        }
    }
}
