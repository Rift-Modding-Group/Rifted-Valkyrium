package org.valkyrienskies.mod.common.compat;

import com.pau101.fairylights.server.capability.CapabilityHandler;
import com.pau101.fairylights.server.fastener.Fastener;
import com.pau101.fairylights.server.fastener.connection.type.Connection;
import com.pau101.fairylights.server.fastener.connection.type.hanginglights.ConnectionHangingLights;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import valkyrienwarfare.api.event.ShipBlockRelocationEvent;

import javax.annotation.Nonnull;

/**
 * Compatibility helpers for Fairy Lights.
 */
public class FairyLightsCompat {
    /**
     * Rebuilds hanging-light geometry and lighting after the assembled ship is registered.
     */
    @SubscribeEvent
    public static void onBlockRelocation(@Nonnull ShipBlockRelocationEvent.PreCopied event) {
        if (!event.isAssembling()) return;

        World world = event.getWorld();
        event.afterRelocation(() -> {
            for (BlockPos source : event.getSourceBlocks()) {
                TileEntity tileEntity = world.getTileEntity(source.add(event.getOffset()));
                if (tileEntity == null) continue;
                Fastener<?> fastener = tileEntity.getCapability(CapabilityHandler.FASTENER_CAP, null);
                if (fastener == null) continue;

                for (Connection connection : fastener.getConnections().values()) {
                    if (!(connection instanceof ConnectionHangingLights hangingLights)) continue;

                    hangingLights.computeCatenary();
                    hangingLights.updateCatenary(fastener.getConnectionPoint());
                }
            }
        });
    }
}
