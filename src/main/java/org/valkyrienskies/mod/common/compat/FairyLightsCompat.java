package org.valkyrienskies.mod.common.compat;

import com.pau101.fairylights.server.capability.CapabilityHandler;
import com.pau101.fairylights.server.fastener.Fastener;
import com.pau101.fairylights.server.fastener.connection.type.Connection;
import com.pau101.fairylights.server.fastener.connection.type.hanginglights.ConnectionHangingLights;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.chunk.Chunk;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;

import javax.annotation.Nonnull;

/**
 * Compatibility helpers for Fairy Lights.
 */
public class FairyLightsCompat {
    /**
     * Rebuilds hanging-light catenaries after a newly assembled ship has been registered.
     *
     * <p>Fairy Lights first builds these connections while the copied chunks are detached from the
     * world and Valkyrien Skies cannot resolve their owning ship yet. Any block light written at
     * that point goes to the old chunk object and is lost when the assembled chunk is installed.</p>
     */
    public static void rebuildShipLighting(@Nonnull PhysicsObject physicsObject) {
        for (Chunk chunk : physicsObject.getClaimedChunkCache()) {
            for (TileEntity tileEntity : chunk.getTileEntityMap().values()) {
                Fastener<?> fastener = tileEntity.getCapability(CapabilityHandler.FASTENER_CAP, null);
                if (fastener == null) continue;

                for (Connection connection : fastener.getConnections().values()) {
                    if (!(connection instanceof ConnectionHangingLights hangingLights)) continue;

                    hangingLights.computeCatenary();
                    hangingLights.updateCatenary(fastener.getConnectionPoint());
                }
            }
        }
    }
}
