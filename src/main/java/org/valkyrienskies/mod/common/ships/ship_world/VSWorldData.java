package org.valkyrienskies.mod.common.ships.ship_world;

import org.valkyrienskies.mod.common.ships.QueryableShipData;
import org.valkyrienskies.mod.common.ships.chunk_claims.ShipChunkAllocator;


public class VSWorldData {

    private final QueryableShipData queryableShipData = new QueryableShipData();

    private final ShipChunkAllocator shipChunkAllocator = new ShipChunkAllocator();

    public QueryableShipData getQueryableShipData() {
        return queryableShipData;
    }

    public ShipChunkAllocator getShipChunkAllocator() {
        return shipChunkAllocator;
    }

}
