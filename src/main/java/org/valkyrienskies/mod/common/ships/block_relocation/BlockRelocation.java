package org.valkyrienskies.mod.common.ships.block_relocation;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.NotNull;
import valkyrienwarfare.api.event.ShipBlockRelocationEvent;

import java.util.ArrayList;
import java.util.List;

public class BlockRelocation {
    /**
     * Captures external block data and returns the actions to run after the destination is ready.
     * */
    @NotNull
    public static Runnable prepare(
            @NotNull World world, @NotNull Iterable<BlockPos> sourceBlocks,
            @NotNull BlockPos offset, boolean assembling
    ) {
        List<Runnable> restorationActions = new ArrayList<>();
        MinecraftForge.EVENT_BUS.post(new ShipBlockRelocationEvent.PreCopied(world, sourceBlocks, offset, assembling,
                restorationActions::add));
        return () -> {
            List<Runnable> pending = List.copyOf(restorationActions);
            restorationActions.clear();
            RuntimeException failure = null;
            for (Runnable action : pending) {
                try {
                    action.run();
                }
                catch (RuntimeException exception) {
                    if (failure == null) failure = exception;
                    else if (failure != exception) failure.addSuppressed(exception);
                }
            }
            if (failure != null) throw failure;
        };
    }
}
