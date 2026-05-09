package org.valkyrienskies.addon.control.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.IThreadListener;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.valkyrienskies.addon.control.ValkyrienSkiesControl;
import org.valkyrienskies.addon.control.capability.controlNodeUser.ICapabilityControlNodeUser;

public class VSStopUsingControlNodeMessage implements IMessage {
    public BlockPos posToStopPiloting;

    public VSStopUsingControlNodeMessage() {}

    public VSStopUsingControlNodeMessage(BlockPos pos) {
        this.posToStopPiloting = pos;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuf = new PacketBuffer(buf);
        this.posToStopPiloting = packetBuf.readBlockPos();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packetBuf = new PacketBuffer(buf);
        packetBuf.writeBlockPos(this.posToStopPiloting);
    }

    public static class Handler implements IMessageHandler<VSStopUsingControlNodeMessage, IMessage> {
        @Override
        public IMessage onMessage(VSStopUsingControlNodeMessage message, MessageContext ctx) {
            IThreadListener mainThread = Minecraft.getMinecraft();
            mainThread.addScheduledTask(() -> {
                ICapabilityControlNodeUser nodeUser = Minecraft.getMinecraft().player.getCapability(ValkyrienSkiesControl.controlNodeUserCapability, null);
                if (nodeUser == null) return;

                BlockPos posToStopPiloting = message.posToStopPiloting;

                if (nodeUser.getUsedControlNodePos() != null && nodeUser.getUsedControlNodePos()
                        .equals(posToStopPiloting)) {
                    nodeUser.stopUsingEverything();
                }
            });
            return null;
        }
    }
}
