package valkyrienwarfare.api;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.config.VSConfig;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

/**
 * Entry point for common ship operations used by addons.
 */
@ParametersAreNonnullByDefault
public class RiftedValkyriumAPI {
    private static Provider provider = new UnavailableProvider();

    private RiftedValkyriumAPI() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Installs the mod-side implementation of this API. Addons must not call this method.
     *
     * @param newProvider the implementation supplied by Rifted Valkyrium
     */
    @ApiStatus.Internal
    public static void registerProvider(Provider newProvider) {
        if (!(provider instanceof UnavailableProvider)) {
            throw new IllegalStateException("The Rifted Valkyrium API provider is already registered");
        }

        provider = Objects.requireNonNull(newProvider, "newProvider");
    }

    /**
     * Gets the loaded physics object managing a block in ship space.
     *
     * @param world the world containing the block
     * @param pos the block position to query
     * @return the loaded physics object, or an empty optional when the block is not on a loaded ship
     */
    @NotNull
    public static Optional<IPhysicsEntity> getPhysoManagingBlock(@Nullable World world, @Nullable BlockPos pos) {
        if (world == null || pos == null) return Optional.empty();
        return provider.getPhysoManagingBlock(world, pos);
    }

    /**
     * A simplified version of {@link getPhysoManagingBlock} that is for testing if a given block pos is in the shipyard
     *
     * @param world the world containing the block
     * @param pos the block position to query
     * @return true if the position is in the shipyard, false otherwise or if inputs are invalid
     */
    public static boolean isPosInShipyard(@Nullable World world, @Nullable BlockPos pos) {
        if (world == null || pos == null) return false;
        return provider.isPosInShipyard(pos);
    }

    /**
     * @return gravitational acceleration in blocks per second squared from {@link VSConfig#doGravity} as a vector
     * */
    @NotNull
    public static Vector3dc getWorldGravity() {
        double gravityY = VSConfig.doGravity ? VSConfig.gravityVecY : 0D;
        return new Vector3d(0D, gravityY, 0D);
    }

    /**
     * Determine the current ship that a given entity is making contact with
     * */
    @NotNull
    public static Optional<IShipData> getActiveShipOfEntity(@Nullable Entity entity) {
        if (entity == null) return Optional.empty();
        return provider.getActiveShipOfEntity(entity);
    }

    /**
     * Gets the persistent ship data managing a block in ship space. This can find unloaded ships as
     * well as loaded ones.
     *
     * @param world the world containing the block
     * @param pos the block position to query
     * @return the ship data, or an empty optional when the block is not in ship space
     */
    @NotNull
    public static Optional<IShipData> getShipManagingBlock(@Nullable World world, @Nullable BlockPos pos) {
        if (world == null || pos == null) return Optional.empty();
        return provider.getShipManagingBlock(world, pos);
    }

    /**
     * Queues a new ship for assembly at a block-aligned world position.
     * <p>
     * Eligible blocks connected to {@code initialPosition} are moved into ship space and the
     * physics object is created during the next server-world tick.
     *
     * @param world the server world that will own the ship
     * @param initialPosition the initial world position of the ship as a {@link BlockPos}
     * @return the data for the queued ship
     */
    @NotNull
    public static IShipData createNewShip(WorldServer world, BlockPos initialPosition) {
        return createNewShip(world, initialPosition, new ShipBuildOptions());
    }

    /**
     * Queues a new ship for assembly at a block-aligned world position.
     *
     * @param world the server world that will own the ship
     * @param initialPosition the initial world position of the ship
     * @param shipBuildOptions a {@link ShipBuildOptions} for passing additional settings to the ship
     * @return the data for the queued ship
     */
    @NotNull
    public static IShipData createNewShip(WorldServer world, BlockPos initialPosition, ShipBuildOptions shipBuildOptions) {
        return createNewShip(
                world,
                new Vec3d(initialPosition.getX(), initialPosition.getY(), initialPosition.getZ()),
                new AxisAlignedBB(initialPosition),
                shipBuildOptions
        );
    }

    /**
     * Queues a new ship at an arbitrary world position. The position is independent
     * of any controller, infuser, or other setter block, allowing an item or another addon-defined
     * assembly system to choose it directly. The containing block position is used as the assembly
     * seed.
     * <p>
     * Eligible connected blocks are moved into ship space and the physics object is created during
     * the next server-world tick.
     *
     * @param world the server world that will own the ship
     * @param initialPosition the initial world position of the ship as a {@link Vec3d}
     * @return the data for the queued ship
     */
    @NotNull
    public static IShipData createNewShip(WorldServer world, Vec3d initialPosition) {
        return createNewShip(world, initialPosition, new ShipBuildOptions());
    }

    /**
     * Queues a new ship at an arbitrary world position.
     *
     * @param world the server world that will own the ship
     * @param initialPosition the initial world position of the ship
     * @param shipBuildOptions a {@link ShipBuildOptions} for passing additional settings to the ship
     * @return the data for the queued ship
     */
    @NotNull
    public static IShipData createNewShip(WorldServer world, Vec3d initialPosition, ShipBuildOptions shipBuildOptions) {
        AxisAlignedBB initialBounds = new AxisAlignedBB(
                initialPosition.x, initialPosition.y, initialPosition.z,
                initialPosition.x + 1D, initialPosition.y + 1D, initialPosition.z + 1D
        );
        return createNewShip(world, initialPosition, initialBounds, shipBuildOptions);
    }

    /**
     * Queues a new ship with caller-defined initial bounds.
     *
     * @param world the server world that will own the ship
     * @param initialPosition the initial world position of the ship as a {@link BlockPos}
     * @param initialBounds the initial world-space bounds of the ship
     * @return the data for the queued ship
     */
    @NotNull
    public static IShipData createNewShip(WorldServer world, BlockPos initialPosition, AxisAlignedBB initialBounds) {
        return createNewShip(world, initialPosition, initialBounds, new ShipBuildOptions());
    }

    /**
     * Queues a new ship with caller-defined initial bounds.
     *
     * @param world the server world that will own the ship
     * @param initialPosition the initial world position of the ship
     * @param initialBounds the initial world-space bounds of the ship
     * @param shipBuildOptions a {@link ShipBuildOptions} for passing additional settings to the ship
     * @return the data for the queued ship
     */
    @NotNull
    public static IShipData createNewShip(
            WorldServer world, BlockPos initialPosition, AxisAlignedBB initialBounds,
            ShipBuildOptions shipBuildOptions
    ) {
        Vec3d position = new Vec3d(initialPosition.getX(), initialPosition.getY(), initialPosition.getZ());
        return createNewShip(world, position, initialBounds, shipBuildOptions);
    }

    /**
     * Queues a new ship at an arbitrary position with caller-defined initial bounds.
     * Connected-block assembly recalculates these bounds from the assembled blocks. The supplied
     * bounds allow other assembly systems to describe ships of any size before that happens.
     *
     * @param world the server world that will own the ship
     * @param initialPosition the initial world position of the ship as a {@link Vec3d}
     * @param initialBounds the initial world-space bounds of the ship
     * @return the data for the queued ship
     */
    @NotNull
    public static IShipData createNewShip(WorldServer world, Vec3d initialPosition, AxisAlignedBB initialBounds) {
        return createNewShip(world, initialPosition, initialBounds, new ShipBuildOptions());
    }

    /**
     * Queues a new ship at an arbitrary position with caller-defined initial bounds.
     *
     * @param world the server world that will own the ship
     * @param initialPosition the initial world position of the ship
     * @param initialBounds the initial world-space bounds of the ship
     * @param shipBuildOptions a {@link ShipBuildOptions} for passing additional settings to the ship
     * @return the data for the queued ship
     */
    @NotNull
    public static IShipData createNewShip(
            WorldServer world, Vec3d initialPosition, AxisAlignedBB initialBounds,
            ShipBuildOptions shipBuildOptions
    ) {
        return provider.createNewShip(world, initialPosition, initialBounds, shipBuildOptions);
    }

    /**
     * Returns a list of ships in the world.
     *
     * @param world the server world to find the ships in
     * @return a non-modifiable list of all ships represented as {@link IShipData} in the world
     * */
    @NotNull
    public static List<IShipData> getAllShips(World world) {
        return provider.getAllShips(world);
    }

    /**
     * Returns a {@link IPhysicsEntity} instance that corresponds to a given {@link IShipData}
     * @param shipData The ship data
     * @return The physics entity, or an empty optional if none could be found
     * */
    public static Optional<IPhysicsEntity> getPhysicsEntity(World world, IShipData shipData) {
        return provider.getPhysicsEntity(world, shipData);
    }

    /**
     * Returns a {@link IShipData} instance that corresponds to a given {@link UUID}
     * @param uuid A {@link UUID}
     * @return The ship data, or an empty optional if none could be found
     * */
    @NotNull
    public static Optional<IShipData> getShip(World world, UUID uuid) {
        return provider.getShipByUUID(world, uuid);
    }

    /**
     * Returns a {@link IShipData} instance that corresponds to a given name
     * @param name A {@link String} that represents its name
     * @return The ship data, or an empty optional if none could be found
     * */
    @NotNull
    public static Optional<IShipData> getShip(World world, String name) {
        return provider.getShipByName(world, name);
    }

    /**
     * Formats a force magnitude using the next SI unit whenever its absolute value reaches four digits.
     * Useful for people trying to render the amount of force their force applying blocks create in a visible manner.
     * @param magnitude The raw magnitude of a force in Newtons.
     * @return A {@link String} that formats it in the largest possible SI unit.
     */
    @NotNull
    public static String formatMagnitude(double magnitude) {
        return formatMagnitude(magnitude, 0);
    }

    @NotNull
    private static String formatMagnitude(double magnitude, int unitIndex) {
        if ((magnitude <= -1000D || magnitude >= 1000D) && unitIndex < 8) {
            return formatMagnitude(magnitude / 1000D, unitIndex + 1);
        }

        String unit = switch (unitIndex) {
            case 1 -> "kN";
            case 2 -> "MN";
            case 3 -> "GN";
            case 4 -> "TN";
            case 5 -> "PN";
            case 6 -> "EN";
            case 7 -> "ZN";
            case 8 -> "YN";
            default -> "N";
        };
        DecimalFormat magnitudeFormat = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return magnitudeFormat.format(magnitude) + " " + unit;
    }

    /**
     * Adds a metadata-specific default mass for an addon block to the Rifted Valkyrium block-mass
     * config. An existing config entry for the same state is preserved so users can override the
     * addon's default.
     *
     * @param blockState the block state whose metadata should receive the default mass
     * @param blockMass the default mass category
     */
    public static void setBlockMass(IBlockState blockState, BlockMass blockMass) {
        provider.setBlockMass(blockState, blockMass);
    }

    /**
     * Adds a default mass for every state of an addon block to the Rifted Valkyrium block-mass
     * config. An existing config entry for the same block is preserved so users can override the
     * addon's default.
     *
     * @param block the block whose states should receive the default mass
     * @param blockMass the default mass category
     */
    public static void setBlockMass(Block block, BlockMass blockMass) {
        provider.setBlockMass(block, blockMass);
    }

    /**
     * Internal service contract implemented by Rifted Valkyrium at runtime.
     */
    @ApiStatus.Internal
    public interface Provider {
        @NotNull
        Optional<IPhysicsEntity> getPhysoManagingBlock(World world, BlockPos pos);

        @NotNull
        Optional<IShipData> getShipManagingBlock(World world, BlockPos pos);

        boolean isPosInShipyard(BlockPos pos);

        @NotNull
        Optional<IShipData> getActiveShipOfEntity(Entity entity);

        void setBlockMass(IBlockState blockState, BlockMass blockMass);

        void setBlockMass(Block block, BlockMass blockMass);

        @NotNull
        IShipData createNewShip(WorldServer world, Vec3d initialPosition, AxisAlignedBB initialBounds, ShipBuildOptions shipBuildOptions);

        @NotNull
        List<IShipData> getAllShips(World world);

        @NotNull
        Optional<IPhysicsEntity> getPhysicsEntity(World world, IShipData shipData);

        @NotNull
        Optional<IShipData> getShipByUUID(World world, UUID uuid);

        @NotNull
        Optional<IShipData> getShipByName(World world, String name);
    }

    private static class UnavailableProvider implements Provider {
        @Override
        @NotNull
        public Optional<IPhysicsEntity> getPhysoManagingBlock(World world, BlockPos pos) {
            return Optional.empty();
        }

        @Override
        @NotNull
        public Optional<IShipData> getShipManagingBlock(World world, BlockPos pos) {
            return Optional.empty();
        }

        @Override
        public boolean isPosInShipyard(BlockPos pos) {
            return false;
        }

        @Override
        @NotNull
        public Optional<IShipData> getActiveShipOfEntity(Entity entity) {
            return Optional.empty();
        }

        @Override
        public void setBlockMass(IBlockState blockState, BlockMass blockMass) {
            throw new IllegalStateException("Rifted Valkyrium has not initialized its API provider");
        }

        @Override
        public void setBlockMass(Block block, BlockMass blockMass) {
            throw new IllegalStateException("Rifted Valkyrium has not initialized its API provider");
        }

        @Override
        @NotNull
        public IShipData createNewShip(WorldServer world, Vec3d initialPosition, AxisAlignedBB initialBounds, ShipBuildOptions shipBuildOptions) {
            throw new IllegalStateException("Rifted Valkyrium has not initialized its API provider");
        }

        @Override
        @NotNull
        public List<IShipData> getAllShips(World world) {
            return List.of();
        }

        @Override
        @NotNull
        public Optional<IPhysicsEntity> getPhysicsEntity(World world, IShipData shipData) {
            return Optional.empty();
        }

        @Override
        @NotNull
        public Optional<IShipData> getShipByUUID(World worldServer, UUID uuid) {
            return Optional.empty();
        }

        @Override
        @NotNull
        public Optional<IShipData> getShipByName(World worldServer, String name) {
            return Optional.empty();
        }
    }
}
