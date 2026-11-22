package org.valkyrienskies.mod.common.compat;

import melonslise.locks.common.capability.ILockableHandler;
import melonslise.locks.common.capability.ILockableStorage;
import melonslise.locks.common.init.LocksCapabilities;
import melonslise.locks.common.init.LocksNetworks;
import melonslise.locks.common.network.toclient.AddLockablePacket;
import melonslise.locks.common.util.Lockable;
import melonslise.locks.common.util.LocksUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import valkyrienwarfare.api.event.ShipBlockRelocationEvent;
import valkyrienwarfare.api.event.ShipChunkSentEvent;

import java.util.ArrayList;
import java.util.List;

public class LocksCompat {
    /**
     * Transfers complete attachments through the shared block relocation event.
     * */
    @SubscribeEvent
    public static void onBlockRelocation(@NotNull ShipBlockRelocationEvent.PreCopied event) {
        World world = event.getWorld();
        ILockableHandler handler = world.getCapability(LocksCapabilities.LOCKABLE_HANDLER, null);
        List<Lockable> relocated = new ArrayList<>();
        if (handler == null) return;

        for (Lockable lockable : new ArrayList<>(handler.getLoaded().values())) {
            boolean touchesMovedBlock = false;
            boolean wholeAttachmentMoves = true;
            for (BlockPos pos : lockable.box.getContainedBlockPositions()) {
                if (event.getSourceBlocks().contains(pos)) touchesMovedBlock = true;
                else if (!world.isBlockLoaded(pos) || !world.isAirBlock(pos)) {
                    wholeAttachmentMoves = false;
                    break;
                }
            }
            if (!touchesMovedBlock || !wholeAttachmentMoves) continue;
            if (handler.remove(lockable.networkID)) {
                relocated.add(new Lockable(
                        lockable.box.offset(event.getOffset()), lockable.lock, lockable.orient,
                        lockable.stack, lockable.networkID
                ));
                lockable.lock.deleteObserver(lockable);
            }
        }
        event.afterRelocation(() -> {
            for (Lockable lockable : relocated) {
                List<ILockableStorage> storages = lockable.box.containedChunksTo(
                        (x, z) -> world.getChunk(x, z).getCapability(LocksCapabilities.LOCKABLE_STORAGE, null),
                        true
                );
                if (storages == null) {
                    throw new IllegalStateException("Missing Locks storage while restoring lock " + lockable.networkID);
                }
                for (ILockableStorage storage : storages) storage.add(lockable);
                handler.getLoaded().put(lockable.networkID, lockable);
                lockable.addObserver(handler);
                LocksUtil.sendToTrackingPlayers(lockable.box, new AddLockablePacket(lockable), world);
            }
        });
    }

    @SubscribeEvent
    public static void onShipChunkSent(@NotNull ShipChunkSentEvent event) {
        ILockableStorage storage = event.getChunk().getCapability(LocksCapabilities.LOCKABLE_STORAGE, null);
        if (storage == null) return;

        storage.get().values().forEach(lockable -> LocksNetworks.MAIN.sendTo(new AddLockablePacket(lockable), event.getPlayer()));
    }
}
