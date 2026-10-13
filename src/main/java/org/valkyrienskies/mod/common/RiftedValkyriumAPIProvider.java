package org.valkyrienskies.mod.common;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.VSWorldDataCapability;
import org.valkyrienskies.mod.common.capability.ship_world.IShipWorld;
import org.valkyrienskies.mod.common.ships.QueryableShipData;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.chunk_claims.ShipChunkAllocator;
import org.valkyrienskies.mod.common.ships.chunk_claims.VSChunkClaim;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import org.valkyrienskies.mod.common.util.names.NounListNameGenerator;
import valkyrienwarfare.api.IPhysicsEntity;
import valkyrienwarfare.api.IShipData;
import valkyrienwarfare.api.RiftedValkyriumAPI;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ParametersAreNonnullByDefault
public class RiftedValkyriumAPIProvider implements RiftedValkyriumAPI.Provider {
    @Override
    @NotNull
    public Optional<IPhysicsEntity> getPhysoManagingBlock(World world, BlockPos pos) {
        Optional<ShipData> shipData = this.findShipManagingBlock(world, pos);
        if (shipData.isEmpty()) return Optional.empty();

        IPhysObjectWorld physObjectWorld = ValkyrienUtils.getPhysObjWorld(world);
        if (physObjectWorld == null) {
            throw new IllegalStateException("Could not get ship manager from world!");
        }

        return shipData.map(data -> physObjectWorld.getPhysObjectFromUUID(data.getUuid()))
                .map(physicsObject -> (IPhysicsEntity) physicsObject);
    }

    @Override
    @NotNull
    public Optional<IShipData> getShipManagingBlock(World world, BlockPos pos) {
        return findShipManagingBlock(world, pos).map(shipData -> (IShipData) shipData);
    }

    @Override
    public boolean isPosInShipyard(BlockPos pos) {
        return ShipChunkAllocator.isBlockInShipyard(pos);
    }

    @Override
    @NotNull
    @SuppressWarnings("deprecation")
    public IShipData createNewShip(WorldServer world, Vec3d initialPosition, AxisAlignedBB initialBounds) {
        Vector3d position = new Vector3d(initialPosition.x, initialPosition.y, initialPosition.z);
        VSWorldDataCapability worldData = this.getWorldDataCapability(world);
        QueryableShipData queryableShipData = worldData.get().getQueryableShipData();
        VSChunkClaim chunkClaim = worldData.get().getShipChunkAllocator().allocateNextChunkClaim();
        BlockPos regionCenter = chunkClaim.getRegionCenter();
        Vector3d centerOfMass = new Vector3d(regionCenter.getX(), regionCenter.getY(), regionCenter.getZ());
        ShipTransform initialTransform = new ShipTransform(position, centerOfMass);
        return ShipData.createData(
                queryableShipData.allShips(), NounListNameGenerator.getInstance().generateName(),
                chunkClaim, UUID.randomUUID(), initialTransform, initialBounds
        );
    }

    @Override
    @NotNull
    public List<IShipData> getAllShips(World world) {
        VSWorldDataCapability worldData = this.getWorldDataCapability(world);
        return List.copyOf(worldData.get().getQueryableShipData().getShips());
    }

    @Override
    @NotNull
    public Optional<IPhysicsEntity> getPhysicsEntity(World world, IShipData shipData) {
        IPhysObjectWorld physObjectWorld = ValkyrienUtils.getPhysObjWorld(world);
        if (physObjectWorld == null) throw new IllegalStateException("Could not get ship manager from world!");
        return physObjectWorld.getAllLoadedThreadSafe().stream()
                .filter(physicsObject -> physicsObject.getUuid().equals(shipData.getUuid()))
                .map(physicsObject -> (IPhysicsEntity) physicsObject)
                .findFirst();
    }

    @Override
    @NotNull
    public Optional<IShipData> getShipByUUID(World world, UUID uuid) {
        return this.getAllShips(world).stream().filter(shipData -> shipData.getUuid().equals(uuid)).findFirst();
    }

    @Override
    @NotNull
    public Optional<IShipData> getShipByName(World world, String name) {
        return this.getAllShips(world).stream().filter(shipData -> shipData.getName().equals(name)).findFirst();
    }

    @NotNull
    private Optional<ShipData> findShipManagingBlock(World world, BlockPos pos) {
        if (!ShipChunkAllocator.isBlockInShipyard(pos)) return Optional.empty();

        return getWorldDataCapability(world).get().getQueryableShipData().getShipFromBlock(pos);
    }

    @NotNull
    private VSWorldDataCapability getWorldDataCapability(World world) {
        VSWorldDataCapability worldData = world.getCapability(VSCapabilityRegistry.VS_WORLD_DATA, null);
        if (worldData == null) {
            throw new IllegalStateException("World " + world + " doesn't have a VSWorldDataCapability. This is wrong!");
        }

        return worldData;
    }
}
