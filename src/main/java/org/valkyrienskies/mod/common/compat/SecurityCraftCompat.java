package org.valkyrienskies.mod.common.compat;

import net.geforcemods.securitycraft.blockentities.SecurityCameraBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.anchored_mount.IShipAnchoredMount;
import valkyrienwarfare.api.event.ShipBlockRelocationEvent;

import java.util.HashMap;
import java.util.Map;

public class SecurityCraftCompat {
    @NotNull
    public static BlockPos getCameraBlockPos(@NotNull Entity camera, @NotNull BlockPos fallback) {
        IShipAnchoredMount anchor = camera.getCapability(VSCapabilityRegistry.VS_SHIP_ANCHORED_MOUNT, null);
        if (anchor != null && anchor.isAnchoredToShip()) return anchor.getLocalAnchorBlock();
        return fallback;
    }

    @SubscribeEvent
    public static void onBlockRelocation(@NotNull ShipBlockRelocationEvent.PreCopied event) {
        World world = event.getWorld();
        if (world.isRemote) return;
        MinecraftServer server = world.getMinecraftServer();
        if (server == null) return;

        Map<BlockPos, BlockPos> cameras = new HashMap<>();
        for (BlockPos source : event.getSourceBlocks()) {
            if (world.getTileEntity(source) instanceof SecurityCameraBlockEntity) {
                cameras.put(source, source.add(event.getOffset()));
            }
        }
        if (cameras.isEmpty()) return;

        event.afterRelocation(() -> {
            SecurityCraftCameraMonitorRelocations relocations = SecurityCraftCameraMonitorRelocations.get(world);
            if (relocations == null) return;
            relocations.record(world.provider.getDimension(), cameras);
            for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
                boolean changed = false;
                for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
                    ItemStack stack = player.inventory.getStackInSlot(slot);
                    changed |= relocations.updateMonitor(stack);
                }
                if (changed) player.inventoryContainer.detectAndSendChanges();
            }
        });
    }
}
