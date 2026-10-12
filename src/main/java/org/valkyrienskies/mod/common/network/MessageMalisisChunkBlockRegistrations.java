package org.valkyrienskies.mod.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.IThreadListener;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.valkyrienskies.mod.common.compat.MalisisCoreCompat;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

/**
 * Synchronizes MalisisCore chunk-block registrations while preserving registrations already
 * present in the receiving chunk.
 */
public class MessageMalisisChunkBlockRegistrations implements IMessage {
    private int chunkX;
    private int chunkZ;
    private Set<BlockPos> registrations;

    public MessageMalisisChunkBlockRegistrations() {
        this.registrations = new HashSet<>();
    }

    public MessageMalisisChunkBlockRegistrations(@Nonnull Chunk chunk, @Nonnull Set<BlockPos> registrations) {
        this.chunkX = chunk.x;
        this.chunkZ = chunk.z;
        this.registrations = Set.copyOf(registrations);
    }

    @Override
    public void fromBytes(@Nonnull ByteBuf buffer) {
        this.chunkX = buffer.readInt();
        this.chunkZ = buffer.readInt();
        int registrationCount = buffer.readInt();
        if (registrationCount < 0 || registrationCount > buffer.readableBytes() / Long.BYTES) {
            throw new IllegalArgumentException("Invalid MalisisCore registration count");
        }

        this.registrations = HashSet.newHashSet(registrationCount);
        for (int index = 0; index < registrationCount; index++) {
            this.registrations.add(BlockPos.fromLong(buffer.readLong()));
        }
    }

    @Override
    public void toBytes(@Nonnull ByteBuf buffer) {
        buffer.writeInt(chunkX);
        buffer.writeInt(chunkZ);
        buffer.writeInt(registrations.size());
        for (BlockPos blockPos : registrations) {
            buffer.writeLong(blockPos.toLong());
        }
    }

    public static class Handler implements IMessageHandler<MessageMalisisChunkBlockRegistrations, IMessage> {
        @Override
        @SuppressWarnings("Convert2Lambda")
        public IMessage onMessage(@Nonnull MessageMalisisChunkBlockRegistrations message, @Nonnull MessageContext context) {
            IThreadListener mainThread = Minecraft.getMinecraft();
            mainThread.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    if (!Loader.isModLoaded("malisiscore")) return;

                    World world = Minecraft.getMinecraft().world;
                    if (world == null) return;

                    Chunk chunk = world.getChunkProvider().getLoadedChunk(message.chunkX, message.chunkZ);
                    if (chunk == null) return;

                    MalisisCoreCompat.mergeChunkBlockRegistrations(chunk, message.registrations);
                }
            });
            return null;
        }
    }
}
