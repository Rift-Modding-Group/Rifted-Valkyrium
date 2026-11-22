package valkyrienwarfare.api.event;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.Event;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Posted on the Forge event bus when checking fluid supply before ship flow cleanup or
 * relocation. The position describes the block's current location, before that block is
 * relocated. The state is the real block state, not stored fluid.
 * <p>
 * {@link Result#ALLOW} treats the block as supported; {@link Result#DENY} treats it as
 * unsupported. {@link Result#DEFAULT} runs the vanilla liquid supply check, which allows
 * non-liquid blocks. This event is not cancellable and does not change the world itself.
 */
@Event.HasResult
public class ShipFluidSupportEvent extends Event {
    private final World world;
    private final BlockPos pos;
    private final IBlockState state;

    @ApiStatus.Internal
    public ShipFluidSupportEvent(@NotNull World world, @NotNull BlockPos pos, @NotNull IBlockState state) {
        this.world = Objects.requireNonNull(world, "world");
        this.pos = Objects.requireNonNull(pos, "pos").toImmutable();
        this.state = Objects.requireNonNull(state, "state");
    }

    @NotNull
    public World getWorld() {
        return this.world;
    }

    @NotNull
    public BlockPos getPos() {
        return this.pos;
    }

    @NotNull
    public IBlockState getState() {
        return this.state;
    }
}
