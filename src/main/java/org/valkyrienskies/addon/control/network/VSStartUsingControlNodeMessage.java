package org.valkyrienskies.addon.control.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.IThreadListener;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.valkyrienskies.addon.control.ValkyrienSkiesControl;
import org.valkyrienskies.addon.control.capability.controlNodeUser.ICapabilityControlNodeUser;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

import java.util.Optional;

public class VSStartUsingControlNodeMessage implements IMessage {
    private BlockPos posUseFrom;

    public VSStartUsingControlNodeMessage() {}

    public VSStartUsingControlNodeMessage(BlockPos posUseFrom) {
        this.posUseFrom = posUseFrom;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuf = new PacketBuffer(buf);

        this.posUseFrom = packetBuf.readBlockPos();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packetBuf = new PacketBuffer(buf);

        packetBuf.writeBlockPos(this.posUseFrom);
    }

    public static class Handler implements IMessageHandler<VSStartUsingControlNodeMessage, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(VSStartUsingControlNodeMessage message, MessageContext messageContext) {
            IThreadListener mainThread = Minecraft.getMinecraft();
            mainThread.addScheduledTask(() -> {
                ICapabilityControlNodeUser nodeUser = Minecraft.getMinecraft().player.getCapability(ValkyrienSkiesControl.controlNodeUserCapability, null);
                if (nodeUser == null) return;

                if (message.posUseFrom != null) {
                    nodeUser.setUsedControlNodePos(message.posUseFrom);
                    Optional<PhysicsObject> physicsObject = ValkyrienUtils.getPhysoManagingBlock(Minecraft.getMinecraft().world, message.posUseFrom);
                    if (physicsObject.isPresent()) {
                        nodeUser.setShip(physicsObject.get());
                    }
                    else {
                        new IllegalStateException("Received incorrect piloting message!").printStackTrace();
                    }
                }

                //if (message.shipPilotingId != null) pilot.setShipIDBeingControlled(message.shipPilotingId);
            });
            return null;
        }
    }
}
