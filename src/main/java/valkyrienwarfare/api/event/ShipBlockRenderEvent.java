package valkyrienwarfare.api.event;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Posted on the Forge event bus on the client before each block is rendered into a ship
 * chunk mesh, for both VBOs and display lists. Posted for every position in each render
 * layer, even if the block itself cannot render in that layer.
 * <p>
 * Handlers can append geometry to the active buffer using the supplied block access and
 * layer. The buffer already has the ship's render translation; handlers must not begin,
 * finish, reset, or change its translation. The position is in ship block coordinates.
 * This event is not cancellable and does not have a result.
 */
@SideOnly(Side.CLIENT)
public class ShipBlockRenderEvent extends Event {
    private final IBlockState state;
    private final BlockPos pos;
    private final IBlockAccess blockAccess;
    private final BufferBuilder buffer;
    private final BlockRenderLayer layer;

    @ApiStatus.Internal
    public ShipBlockRenderEvent(
            @NotNull IBlockState state, @NotNull BlockPos pos, @NotNull IBlockAccess blockAccess,
            @NotNull BufferBuilder buffer, @NotNull BlockRenderLayer layer
    ) {
        this.state = Objects.requireNonNull(state, "state");
        this.pos = Objects.requireNonNull(pos, "pos").toImmutable();
        this.blockAccess = Objects.requireNonNull(blockAccess, "blockAccess");
        this.buffer = Objects.requireNonNull(buffer, "buffer");
        this.layer = Objects.requireNonNull(layer, "layer");
    }

    @NotNull
    public IBlockState getState() {
        return this.state;
    }

    @NotNull
    public BlockPos getPos() {
        return this.pos;
    }

    @NotNull
    public IBlockAccess getBlockAccess() {
        return this.blockAccess;
    }

    @NotNull
    public BufferBuilder getBuffer() {
        return this.buffer;
    }

    @NotNull
    public BlockRenderLayer getLayer() {
        return this.layer;
    }
}
