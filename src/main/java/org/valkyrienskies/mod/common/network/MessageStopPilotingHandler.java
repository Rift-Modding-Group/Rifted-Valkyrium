package org.valkyrienskies.mod.common.network;

import net.minecraft.client.Minecraft;
import net.minecraft.util.IThreadListener;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.ship_pilot.IShipPilot;

public class MessageStopPilotingHandler implements IMessageHandler<MessageStopPiloting, IMessage> {

    @Override
    public IMessage onMessage(MessageStopPiloting message, MessageContext ctx) {
        IThreadListener mainThread = Minecraft.getMinecraft();
        mainThread.addScheduledTask(() -> {
            IShipPilot pilot = Minecraft.getMinecraft().player.getCapability(VSCapabilityRegistry.VS_SHIP_PILOT, null);

            BlockPos posToStopPiloting = message.posToStopPiloting;

            if (pilot.getPosBeingControlled() != null && pilot.getPosBeingControlled()
                .equals(posToStopPiloting)) {
                pilot.stopPilotingEverything();
            }
        });
        return null;
    }

}
