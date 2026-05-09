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
import org.valkyrienskies.addon.control.gui.IVSTileGui;

/**
 * Used to tell the server when a client has pressed a button from a VS TileEntity with a gui.
 */
public class VSGuiButtonMessage implements IMessage {
    private BlockPos tileEntityPos;
    private int buttonId;

    /**
     * Server constructor
     */
    @SuppressWarnings("unused")
    public VSGuiButtonMessage() {}

    /**
     * Client constructor
     *
     * @param tileEntity
     * @param buttonId
     */
    public VSGuiButtonMessage(TileEntity tileEntity, int buttonId) {
        this.tileEntityPos = tileEntity.getPos();
        this.buttonId = buttonId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        tileEntityPos = packetBuffer.readBlockPos();
        buttonId = packetBuffer.readVarInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeBlockPos(tileEntityPos);
        packetBuffer.writeVarInt(buttonId);
    }

    public BlockPos getTileEntityPos() {
        return tileEntityPos;
    }

    public int getButtonId() {
        return buttonId;
    }

    public static class Handler implements IMessageHandler<VSGuiButtonMessage, IMessage> {
        @Override
        public IMessage onMessage(VSGuiButtonMessage message, MessageContext ctx) {
            IThreadListener mainThread = ctx.getServerHandler().server;
            mainThread.addScheduledTask(() -> {
                World playerWorld = ctx.getServerHandler().player.world;
                TileEntity tileEntity = playerWorld.getTileEntity(message.getTileEntityPos());
                if (tileEntity == null) {
                    // Nothing there, ignore this message
                    return;
                }
                int buttonId = message.getButtonId();
                // Tell the tile entity that this player tried pressing the given button.
                if (tileEntity instanceof IVSTileGui) {
                    ((IVSTileGui) tileEntity).onButtonPress(buttonId, ctx.getServerHandler().player);
                }
            });
            return null;
        }
    }
}
