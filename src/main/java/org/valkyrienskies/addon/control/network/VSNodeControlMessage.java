package org.valkyrienskies.addon.control.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IThreadListener;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.valkyrienskies.addon.control.tileentity.ITileEntityControlNode;

public class VSNodeControlMessage implements IMessage {
    private BlockPos controllerPos;
    private int usedControls;

    public VSNodeControlMessage() {}

    public VSNodeControlMessage(BlockPos controllerPos, int usedControls) {
        this.controllerPos = controllerPos;
        this.usedControls = usedControls;
    }

    public int getUsedControls() {
        return this.usedControls;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuf = new PacketBuffer(buf);

        this.controllerPos = packetBuf.readBlockPos();
        this.usedControls = packetBuf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packetBuf = new PacketBuffer(buf);

        packetBuf.writeBlockPos(this.controllerPos);
        packetBuf.writeInt(this.usedControls);
    }

    public static class Handler implements IMessageHandler<VSNodeControlMessage, IMessage> {
        @Override
        public IMessage onMessage(VSNodeControlMessage message, MessageContext ctx) {
            IThreadListener mainThread = ctx.getServerHandler().server;
            mainThread.addScheduledTask(() -> {
                if (message.controllerPos != null) {
                    World worldObj = ctx.getServerHandler().player.world;
                    BlockPos posFor = message.controllerPos;
                    TileEntity tile = worldObj.getTileEntity(posFor);

                    if (tile instanceof ITileEntityControlNode controlNode) {
                        controlNode.onNodeControlsMessage(message, ctx.getServerHandler().player);
                    }
                }
            });
            return null;
        }
    }
}
