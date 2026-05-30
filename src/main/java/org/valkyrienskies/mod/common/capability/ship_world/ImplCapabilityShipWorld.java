package org.valkyrienskies.mod.common.capability.ship_world;

import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;

import java.util.function.Function;

public class ImplCapabilityShipWorld implements IShipWorld {
    //

    //ship manager stuff
    private IPhysObjectWorld manager = null;

    //-----originally from IWorldVS-----
    @Override
    public void excludeShipFromRayTracer(PhysicsObject entity) {

    }

    @Override
    public void unexcludeShipFromRayTracer(PhysicsObject entity) {

    }

    @Override
    public RayTraceResult rayTraceBlocksIgnoreShip(Vec3d vec31, Vec3d vec32, boolean stopOnLiquid, boolean ignoreBlockWithoutBoundingBox, boolean returnLastUncollidableBlock, PhysicsObject toIgnore) {
        return null;
    }

    @Override
    public RayTraceResult rayTraceBlocksInShip(Vec3d vec31, Vec3d vec32, boolean stopOnLiquid, boolean ignoreBlockWithoutBoundingBox, boolean returnLastUncollidableBlock, PhysicsObject toUse) {
        return null;
    }

    //-----originally from IHasShipManager-----
    @Override
    public IPhysObjectWorld getManager() {
        return this.manager;
    }

    @Override
    public void setManager(IPhysObjectWorld physObjectWorld) {
        this.manager = physObjectWorld;
    }
}
