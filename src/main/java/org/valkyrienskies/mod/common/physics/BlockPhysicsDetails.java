package org.valkyrienskies.mod.common.physics;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.block.IBlockForceProvider;
import org.valkyrienskies.mod.common.block.IBlockTorqueProvider;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class BlockPhysicsDetails {
    /**
     * Blocks mapped to their mass.
     */
    private static final Map<Block, Mass> blockToMass = new HashMap<>();
    /**
     * Materials mapped to their mass.
     */
    private static final Map<Material, Mass> materialMass = new HashMap<>();
    /**
     * Blocks that should not be infused with physics.
     */
    public static final ArrayList<Block> blocksToNotPhysicsInfuse = new ArrayList<>();

    static {
        generateBlockMasses();
        generateMaterialMasses();
        generateBlocksToNotPhysicsInfuse();

        VSConfig.registerSyncEvent(BlockPhysicsDetails::onSync);
        onSync();
    }

    private static void onSync() {
        Arrays.stream(VSConfig.blockMass)
            .map(str -> str.split("="))
            .filter(arr -> arr.length == 2)
            .forEach(arr ->
                blockToMass.put(Block.getBlockFromName(arr[0]), Mass.valueOf(arr[1])));
    }

    private static void generateMaterialMasses() {
        materialMass.put(Material.AIR, Mass.NONE);
        materialMass.put(Material.ANVIL, Mass.HEAVY);
        materialMass.put(Material.BARRIER, Mass.NONE);
        materialMass.put(Material.CACTUS, Mass.LIGHT);
        materialMass.put(Material.CAKE, Mass.NONE);
        materialMass.put(Material.CARPET, Mass.NONE);
        materialMass.put(Material.CIRCUITS, Mass.NONE);
        materialMass.put(Material.CLAY, Mass.HEAVY);
        materialMass.put(Material.CLOTH, Mass.NONE);
        materialMass.put(Material.CORAL, Mass.HEAVY);
        materialMass.put(Material.CRAFTED_SNOW, Mass.LIGHT);
        materialMass.put(Material.DRAGON_EGG, Mass.LIGHT);
        materialMass.put(Material.FIRE, Mass.NONE);
        materialMass.put(Material.GLASS, Mass.LIGHT);
        materialMass.put(Material.GOURD, Mass.LIGHT);
        materialMass.put(Material.GRASS, Mass.LIGHT);
        materialMass.put(Material.GROUND, Mass.LIGHT);
        materialMass.put(Material.ICE, Mass.LIGHT);
        materialMass.put(Material.IRON, Mass.HEAVY);
        materialMass.put(Material.LAVA, Mass.LIGHT);
        materialMass.put(Material.LEAVES, Mass.NONE);
        materialMass.put(Material.PACKED_ICE, Mass.LIGHT);
        materialMass.put(Material.PISTON, Mass.LIGHT);
        materialMass.put(Material.PLANTS, Mass.NONE);
        materialMass.put(Material.PORTAL, Mass.NONE);
        materialMass.put(Material.REDSTONE_LIGHT, Mass.NONE);
        materialMass.put(Material.ROCK, Mass.HEAVY);
        materialMass.put(Material.SAND, Mass.LIGHT);
        materialMass.put(Material.SNOW, Mass.LIGHT);
        materialMass.put(Material.SPONGE, Mass.LIGHT);
        materialMass.put(Material.STRUCTURE_VOID, Mass.LIGHT);
        materialMass.put(Material.TNT, Mass.LIGHT);
        materialMass.put(Material.VINE, Mass.LIGHT);
        materialMass.put(Material.WATER, Mass.LIGHT);
        materialMass.put(Material.WEB, Mass.NONE);
        materialMass.put(Material.WOOD, Mass.LIGHT);
    }

    private static void generateBlockMasses() {
        blockToMass.put(Blocks.AIR, Mass.NONE);
        blockToMass.put(Blocks.FIRE, Mass.NONE);
        blockToMass.put(Blocks.FLOWING_WATER, Mass.NONE);
        blockToMass.put(Blocks.FLOWING_LAVA, Mass.NONE);
        blockToMass.put(Blocks.WATER, Mass.NONE);
        blockToMass.put(Blocks.LAVA, Mass.NONE);
        blockToMass.put(Blocks.BEDROCK, Mass.VERY_HEAVY);
    }

    private static void generateBlocksToNotPhysicsInfuse() {
        blocksToNotPhysicsInfuse.add(Blocks.AIR);
        blocksToNotPhysicsInfuse.add(Blocks.WATER);
        blocksToNotPhysicsInfuse.add(Blocks.FLOWING_WATER);
        blocksToNotPhysicsInfuse.add(Blocks.LAVA);
        blocksToNotPhysicsInfuse.add(Blocks.FLOWING_LAVA);
    }

    /**
     * Get block mass, in kg.
     */
    public static double getMassFromState(IBlockState state) {
        return getMassOfBlock(state.getBlock());
    }

    private static double getMassOfMaterial(Material material) {
        return materialMass.getOrDefault(material, Mass.LIGHT).mass;
    }

    private static double getMassOfBlock(Block block) {
        if (block instanceof BlockLiquid) return Mass.NONE.mass;
        else if (blockToMass.get(block) != null) {
            return blockToMass.get(block).mass;
        }
        else {
            return getMassOfMaterial(block.getDefaultState().getMaterial());
        }
    }

    /**
     * Assigns the output parameter of toSet to be the force Vector for the given IBlockState.
     */
    static void getForceFromState(IBlockState state, BlockPos pos, World world,
        double secondsToApply,
        PhysicsObject obj, Vector3d toSet) {
        Block block = state.getBlock();
        if (block instanceof IBlockForceProvider) {
            Vector3dc forceVector = ((IBlockForceProvider) block).getBlockForceInWorldSpace(world, pos, state, obj, secondsToApply);
            if (forceVector == null) toSet.zero();
            else {
                toSet.x = forceVector.x();
                toSet.y = forceVector.y();
                toSet.z = forceVector.z();
            }
        }
    }

    /**
     * Returns true if the given IBlockState can create force; otherwise it returns false.
     */
    public static boolean isBlockProvidingForce(IBlockState state) {
        Block block = state.getBlock();
        return block instanceof IBlockForceProvider || block instanceof IBlockTorqueProvider;
    }

    /**
     * Each mass enum is to have a fixed numerical mass.
     * */
    public enum Mass {
        NONE(0),
        LIGHT(500),
        HEAVY(8000),
        VERY_HEAVY(20000);

        public final int mass;

        Mass(int mass) {
            this.mass = mass;
        }
    }
}
