package org.valkyrienskies.mod.common.network;

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
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.ship_pilot.IShipPilot;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

import java.util.Optional;
import java.util.UUID;

public class MessageStartPiloting implements IMessage {
    public UUID shipPilotingId;
    public BlockPos posToStartPiloting;

    public MessageStartPiloting(BlockPos posToStartPiloting) {
        this.posToStartPiloting = posToStartPiloting;
        this.shipPilotingId = null;
    }

    public MessageStartPiloting(UUID shipPilotingId) {
        this.posToStartPiloting = null;
        this.shipPilotingId = shipPilotingId;
    }

    /**
     * All IMessage instances must have a no argument constructor.
     */
    @SuppressWarnings("unused")
    public MessageStartPiloting() {}

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuf = new PacketBuffer(buf);
        final boolean hasPosToPilot = packetBuf.readBoolean();
        final boolean hasShipToPilot = packetBuf.readBoolean();

        if (hasPosToPilot) {
            this.posToStartPiloting = new BlockPos(
                    packetBuf.readInt(),
                    packetBuf.readInt(),
                    packetBuf.readInt()
            );
        }
        if (hasShipToPilot) {
            this.shipPilotingId = packetBuf.readUniqueId();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packetBuf = new PacketBuffer(buf);

        packetBuf.writeBoolean(this.posToStartPiloting != null);
        packetBuf.writeBoolean(this.shipPilotingId != null);

        if (this.posToStartPiloting != null) {
            packetBuf.writeInt(this.posToStartPiloting.getX());
            packetBuf.writeInt(this.posToStartPiloting.getY());
            packetBuf.writeInt(this.posToStartPiloting.getZ());
        }

        if (this.shipPilotingId != null) {
            packetBuf.writeUniqueId(this.shipPilotingId);
        }
    }

    public static class Handler implements IMessageHandler<MessageStartPiloting, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(MessageStartPiloting message, MessageContext ctx) {
            IThreadListener mainThread = Minecraft.getMinecraft();
            mainThread.addScheduledTask(() -> {
                IShipPilot pilot = Minecraft.getMinecraft().player.getCapability(VSCapabilityRegistry.VS_SHIP_PILOT, null);
                if (pilot == null) return;

                if (message.posToStartPiloting != null) {
                    pilot.setPosBeingControlled(message.posToStartPiloting);
                    Optional<PhysicsObject> physicsObject = ValkyrienUtils.getPhysoManagingBlock(Minecraft.getMinecraft().world, message.posToStartPiloting);
                    if (physicsObject.isPresent()) {
                        pilot.setPilotedShip(physicsObject.get());
                    }
                    else {
                        new IllegalStateException("Received incorrect piloting message!").printStackTrace();
                    }
                }

                if (message.shipPilotingId != null) pilot.setShipIDBeingControlled(message.shipPilotingId);
            });
            return null;
        }
    }
}
