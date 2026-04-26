package org.valkyrienskies.addon.world.worldgen

import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraft.world.chunk.IChunkProvider
import net.minecraft.world.gen.IChunkGenerator
import net.minecraft.world.gen.feature.WorldGenMinable
import net.minecraft.world.gen.feature.WorldGenerator
import net.minecraftforge.fml.common.IWorldGenerator
import org.valkyrienskies.addon.world.ValkyrienSkiesWorld
import org.valkyrienskies.addon.world.config.VSWorldConfig
import java.util.*

/**
 * Created by joeyr on 4/18/2017.
 */
class ValkyrienSkiesWorldGen : IWorldGenerator {

    var genValkyriumOre: WorldGenMinable? = null

    override fun generate(random: Random, chunkX: Int, chunkZ: Int, world: World,
                          chunkGenerator: IChunkGenerator, chunkProvider: IChunkProvider) {
        if (VSWorldConfig.valkyriumOreGenEnabled) {
            if (genValkyriumOre == null) {
                genValkyriumOre = WorldGenMinable(
                        ValkyrienSkiesWorld.INSTANCE.valkyriumOre.defaultState, 8)
            }
            when (world.provider.dimension) {
                0 -> runValkyriumGenerator(genValkyriumOre, world, random, chunkX, chunkZ, 2,
                        0, 25)
                -1 -> {
                    // nether
                }
                1 -> {
                    // end
                }
            }
        }
    }

    private fun runValkyriumGenerator(generator: WorldGenerator?, world: World, rand: Random,
                                      chunk_X: Int, chunk_Z: Int, chancesToSpawn: Int, minHeight: Int, maxHeight: Int) {
        require(!(minHeight < 0 || maxHeight > 256 || minHeight > maxHeight)) {
            "Illegal Height Arguments for WorldGenerator"
        }

        val heightDiff = maxHeight - minHeight + 1
        for (i in 0 until chancesToSpawn) {
            val x = chunk_X * 16 + rand.nextInt(16)
            val y = minHeight + rand.nextInt(heightDiff)
            val z = chunk_Z * 16 + rand.nextInt(16)
            generator!!.generate(world, rand, BlockPos(x, y, z))
        }
    }
}