package org.valkyrienskies.mod.client.compat;

import com.pau101.fairylights.server.capability.CapabilityHandler;
import com.pau101.fairylights.server.fastener.Fastener;
import com.pau101.fairylights.server.fastener.connection.type.Connection;
import com.pau101.fairylights.server.fastener.connection.type.hanginglights.ConnectionHangingLights;
import com.pau101.fairylights.server.fastener.connection.type.hanginglights.Light;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import org.joml.Vector3d;
import org.valkyrienskies.mod.client.render.ShipDynamicLighting;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

@ParametersAreNonnullByDefault
public class FairyLightsLighting {
    public static void collectLights(
            Iterable<Chunk> chunks, ShipTransform tickTransform, ShipTransform renderTransform, float partialTicks,
            List<ShipDynamicLighting.Light> lights
    ) {
        for (Chunk chunk : chunks) {
            if (chunk == null || !chunk.getWorld().isRemote) continue;

            for (TileEntity tile : chunk.getTileEntityMap().values()) {
                if (tile.isInvalid()) continue;

                Fastener<?> fastener = tile.getCapability(CapabilityHandler.FASTENER_CAP, null);
                if (fastener == null) continue;

                for (Connection connection : fastener.getConnections().values()) {
                    if (!(connection instanceof ConnectionHangingLights strand) || !strand.isOrigin()
                            || strand.getCatenary() == null || !strand.getDestination().isLoaded(chunk.getWorld())
                    ) {
                        continue;
                    }

                    Vec3d anchor = fastener.getConnectionPoint();
                    Vector3d renderAnchor = new Vector3d(anchor.x, anchor.y, anchor.z);
                    tickTransform.getGlobalToSubspace().transformPosition(renderAnchor);
                    renderTransform.getSubspaceToGlobal().transformPosition(renderAnchor);
                    Light[] current = strand.getFeatures();
                    Light[] previous = strand.getPrevFeatures();
                    for (int i = 0; i < Math.min(current.length, previous.length); i++) {
                        int level = Math.clamp(Math.round(current[i].getBrightness(partialTicks) * 15f), 0, 15);
                        if (level == 0) continue;

                        Vec3d point = previous[i].getPoint().scale(1D - partialTicks)
                                .add(current[i].getPoint().scale(partialTicks)).scale(1D / 16D);
                        lights.add(new ShipDynamicLighting.Light(
                                renderAnchor.x + point.x,
                                renderAnchor.y + point.y,
                                renderAnchor.z + point.z, level
                        ));
                    }
                }
            }
        }
    }
}
