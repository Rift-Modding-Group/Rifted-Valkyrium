package org.valkyrienskies.mod.common.capability.ship_world;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

import java.util.List;

public class ImplCapabilityShipWorld implements IShipWorld {
    //---old iworldvs stuff---
    // Raytrace exclusion is call-context state, not world instance state.
    private final ThreadLocal<PhysicsObject> dontInterceptShip = ThreadLocal.withInitial(() -> null);
    private boolean shouldInterceptRayTrace = true;

    //---ship manager stuff---
    private IPhysObjectWorld manager = null;

    //-----originally from IWorldVS-----
    @Override
    public void excludeShipFromRayTracer(PhysicsObject entity) {
        if (this.dontInterceptShip.get() != null) {
            throw new IllegalStateException("excluded ship is already set!");
        }
        this.dontInterceptShip.set(entity);
    }

    @Override
    public void unexcludeShipFromRayTracer(PhysicsObject entity) {
        if (this.dontInterceptShip.get() != entity) {
            throw new IllegalStateException("must exclude the same ship!");
        }
        this.dontInterceptShip.remove();
    }

    @Override
    public RayTraceResult rayTraceBlocksIgnoreShip(
            World world, Vec3d vec31, Vec3d vec32, boolean stopOnLiquid,
            boolean ignoreBlockWithoutBoundingBox, boolean returnLastUncollidableBlock, PhysicsObject toIgnore
    ) {
        this.shouldInterceptRayTrace = false;
        RayTraceResult vanillaTrace = world.rayTraceBlocks(
                vec31, vec32, stopOnLiquid,
                ignoreBlockWithoutBoundingBox, returnLastUncollidableBlock
        );

        IPhysObjectWorld physObjectWorld = null;
        if (ValkyrienUtils.notInFakeWorldBlacklist(world)) {
            physObjectWorld = this.manager;
        }

        if (physObjectWorld == null) return vanillaTrace;

        Vec3d playerReachVector = vec32.subtract(vec31);

        AxisAlignedBB playerRangeBB = new AxisAlignedBB(
                vec31.x, vec31.y, vec31.z,
                vec32.x, vec32.y, vec32.z
        );

        List<PhysicsObject> nearbyShips = physObjectWorld.getPhysObjectsInAABB(playerRangeBB);
        // Get rid of the Ship that we're not supposed to be RayTracing for
        nearbyShips.remove(toIgnore);

        double reachDistance = playerReachVector.length();
        double worldResultDistFromPlayer = 420000000D;
        if (vanillaTrace != null && vanillaTrace.hitVec != null) {
            worldResultDistFromPlayer = vanillaTrace.hitVec.distanceTo(vec31);
        }

        for (PhysicsObject wrapper : nearbyShips) {
            Vec3d playerEyesPos = vec31;
            playerReachVector = vec32.subtract(vec31);

            ShipTransform shipTransform = wrapper.getShipTransformationManager()
                    .getRenderTransform();

            playerEyesPos = shipTransform.transform(playerEyesPos,
                    TransformType.GLOBAL_TO_SUBSPACE);
            playerReachVector = shipTransform.rotate(playerReachVector,
                    TransformType.GLOBAL_TO_SUBSPACE);

            Vec3d playerEyesReachAdded = playerEyesPos.add(
                    playerReachVector.x * reachDistance,
                    playerReachVector.y * reachDistance,
                    playerReachVector.z * reachDistance
            );
            RayTraceResult resultInShip = world.rayTraceBlocks(
                    playerEyesPos, playerEyesReachAdded,
                    stopOnLiquid, ignoreBlockWithoutBoundingBox, returnLastUncollidableBlock
            );
            if (resultInShip != null && resultInShip.hitVec != null
                    && resultInShip.typeOfHit == RayTraceResult.Type.BLOCK) {
                double shipResultDistFromPlayer = resultInShip.hitVec.distanceTo(playerEyesPos);
                if (shipResultDistFromPlayer < worldResultDistFromPlayer) {
                    worldResultDistFromPlayer = shipResultDistFromPlayer;
                    // The hitVec must ALWAYS be in global coordinates.
                    resultInShip.hitVec = shipTransform
                            .transform(resultInShip.hitVec, TransformType.SUBSPACE_TO_GLOBAL);
                    vanillaTrace = resultInShip;
                }
            }
        }

        this.shouldInterceptRayTrace = true;
        return vanillaTrace;
    }

    @Override
    public RayTraceResult rayTraceBlocksInShip(
            World world, Vec3d vec31, Vec3d vec32, boolean stopOnLiquid,
            boolean ignoreBlockWithoutBoundingBox, boolean returnLastUncollidableBlock, PhysicsObject toUse
    ) {
        this.shouldInterceptRayTrace = false;

        final ShipTransform shipTransform = toUse.getShipTransformationManager()
                .getRenderTransform();

        final Vec3d traceStart = shipTransform.transform(vec31,
                TransformType.GLOBAL_TO_SUBSPACE);
        final Vec3d traceEnd = shipTransform.transform(vec32,
                TransformType.GLOBAL_TO_SUBSPACE);

        final RayTraceResult resultInShip = world.rayTraceBlocks(
                traceStart, traceEnd,
                stopOnLiquid, ignoreBlockWithoutBoundingBox, returnLastUncollidableBlock
        );
        if (resultInShip != null && resultInShip.hitVec != null && resultInShip.typeOfHit == RayTraceResult.Type.BLOCK) {
            // The hitVec must ALWAYS be in global coordinates.
            resultInShip.hitVec = shipTransform
                    .transform(resultInShip.hitVec, TransformType.SUBSPACE_TO_GLOBAL);
            this.shouldInterceptRayTrace = true;
            return resultInShip;
        }

        this.shouldInterceptRayTrace = true;
        return null;
    }

    @Override
    public boolean getShouldInterceptRayTrace() {
        return this.shouldInterceptRayTrace;
    }

    @Override
    public void setShouldInterceptRayTrace(boolean value) {
        this.shouldInterceptRayTrace = value;
    }
    @Override
    public ThreadLocal<PhysicsObject> getDontInterceptShip() {
        return this.dontInterceptShip;
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
