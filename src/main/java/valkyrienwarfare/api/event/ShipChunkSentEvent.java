package valkyrienwarfare.api.event;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.eventhandler.Event;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Posted on the Forge event bus on the server after a ship chunk's terrain packet is queued
 * for a player. This event is not cancellable and does not acknowledge client receipt.
 * <p>
 * Handlers can send additional chunk data to {@link #getPlayer()} after the terrain packet.
 * This event is posted for each ship chunk sent to each player, including when tracking resumes;
 * it is not posted for ordinary terrain chunks or subsequent block updates.
 */
public class ShipChunkSentEvent extends Event {
    private final Chunk chunk;
    private final EntityPlayerMP player;

    @ApiStatus.Internal
    public ShipChunkSentEvent(@NotNull Chunk chunk, @NotNull EntityPlayerMP player) {
        this.chunk = Objects.requireNonNull(chunk, "chunk");
        this.player = Objects.requireNonNull(player, "player");
    }

    @NotNull
    public Chunk getChunk() {
        return this.chunk;
    }

    @NotNull
    public EntityPlayerMP getPlayer() {
        return this.player;
    }
}
