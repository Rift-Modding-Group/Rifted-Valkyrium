package org.valkyrienskies.addon.world.block

import net.minecraft.block.Block
import net.minecraft.block.BlockFalling
import net.minecraft.block.material.Material
import net.minecraft.block.state.IBlockState
import net.minecraft.client.resources.I18n
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.init.Blocks
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.TextFormatting
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import org.valkyrienskies.addon.world.EntityFallingUpBlock
import org.valkyrienskies.addon.world.ValkyrienSkiesWorld
import org.valkyrienskies.addon.world.util.BaseBlock
import java.util.*

class BlockValkyriumOre : BaseBlock("valkyrium_ore", Material.ROCK, 3.0f / 15.0f, true) {
    override fun addInformation(stack: ItemStack, player: World?,
                                itemInformation: MutableList<String>, advanced: ITooltipFlag) {
        itemInformation.add(TextFormatting.ITALIC.toString() + "" + TextFormatting.BLUE + TextFormatting.ITALIC + I18n.format("tooltip.vs_world.valkyrium_ore_1"))
        itemInformation.add(TextFormatting.ITALIC.toString() + "" + TextFormatting.BLUE + TextFormatting.ITALIC + I18n.format("tooltip.vs_world.valkyrium_ore_2"))
    }

    override fun onBlockAdded(worldIn: World, pos: BlockPos, state: IBlockState) {
        worldIn.scheduleUpdate(pos, this, tickRate(worldIn))
    }

    override fun neighborChanged(state: IBlockState, worldIn: World, pos: BlockPos, blockIn: Block,
                                 fromPos: BlockPos) {
        worldIn.scheduleUpdate(pos, this, tickRate(worldIn))
    }

    override fun tickRate(worldIn: World): Int {
        return 2
    }

    override fun updateTick(worldIn: World, pos: BlockPos, state: IBlockState, rand: Random) {
        if (!worldIn.isRemote) {
            tryFallingUp(worldIn, pos)
        }
    }

    private fun tryFallingUp(worldIn: World, pos: BlockPos) {
        val downPos = pos.up()
        if ((worldIn.isAirBlock(downPos) || canFallThrough(worldIn.getBlockState(downPos)))
                && pos.y >= 0) {
            if (!BlockFalling.fallInstantly && worldIn
                            .isAreaLoaded(pos.add(-32, -32, -32), pos.add(32, 32, 32))) {
                if (!worldIn.isRemote) {
                    // Start falling up
                    val entityfallingblock = EntityFallingUpBlock(worldIn,
                            pos.x.toDouble() + 0.5, pos.y.toDouble(), pos.z.toDouble() + 0.5,
                            worldIn.getBlockState(pos))
                    worldIn.spawnEntity(entityfallingblock)
                }
            } else {
                val state = worldIn.getBlockState(pos)
                worldIn.setBlockToAir(pos)
                var blockpos: BlockPos
                blockpos = pos.up()
                while ((worldIn.isAirBlock(blockpos) || canFallThrough(
                                worldIn.getBlockState(blockpos))) && blockpos.y < 255) {
                    blockpos = blockpos.up()
                }
                if (blockpos.y < 255) {
                    worldIn.setBlockState(blockpos.down(), state, 3)
                }
            }
        }
    }

    //Ore Properties Start Here
    override fun getItemDropped(state: IBlockState, rand: Random, fortune: Int): Item? {
        return ValkyrienSkiesWorld.INSTANCE.valkyriumCrystal
    }

    override fun quantityDroppedWithBonus(fortune: Int, random: Random): Int {
        return this.quantityDropped(random) + random.nextInt(fortune + 1)
    }

    /**
     * Returns the quantity of items to drop on block destruction.
     */
    override fun quantityDropped(random: Random): Int {
        return 4 + random.nextInt(4)
    }

    override fun getExpDrop(state: IBlockState, world: IBlockAccess, pos: BlockPos,
                            fortune: Int): Int {
        return if (getItemDropped(state, Block.RANDOM, fortune) !== Item.getItemFromBlock(this)) {
            16 + Block.RANDOM.nextInt(10)
        } else 0
    }

    companion object {
        // Ripped from BlockFalling class for consistancy with game mechanics
        fun canFallThrough(state: IBlockState): Boolean {
            val block = state.block
            val material = state.material
            return block === Blocks.FIRE || material === Material.AIR || material === Material.WATER || material === Material.LAVA
        }
    }

    init {
        setHardness(3.0f)
    }
}