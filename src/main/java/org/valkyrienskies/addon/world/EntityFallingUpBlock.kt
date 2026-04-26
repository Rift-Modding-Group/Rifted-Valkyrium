package org.valkyrienskies.addon.world

import net.minecraft.block.BlockFalling
import net.minecraft.block.ITileEntityProvider
import net.minecraft.block.material.Material
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.MoverType
import net.minecraft.entity.item.EntityFallingBlock
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Like a regular falling block, but it falls up.
 *
 * @author thebest108
 */
class EntityFallingUpBlock : EntityFallingBlock {

    constructor(worldIn: World) : super(worldIn) {
        fallTile = ValkyrienSkiesWorld.INSTANCE.valkyriumOre.defaultState
    }

    constructor(worldIn: World, x: Double, y: Double, z: Double,
                fallingBlockState: IBlockState) : super(worldIn, x, y, z, fallingBlockState)

    override fun onUpdate() {
        val block = fallTile.block
        if (fallTile.material === Material.AIR) {
            setDead()
        } else {
            prevPosX = posX
            prevPosY = posY
            prevPosZ = posZ
            if (fallTime++ == 0) {
                val blockpos = BlockPos(this)
                if (world.getBlockState(blockpos).block === block) {
                    world.setBlockToAir(blockpos)
                } else if (!world.isRemote) {
                    setDead()
                    return
                }
            }
            if (!hasNoGravity()) {
                motionY += 0.03999999910593033
            }
            move(MoverType.SELF, motionX, motionY, motionZ)
            motionX *= 0.9800000190734863
            motionY *= 0.9800000190734863
            motionZ *= 0.9800000190734863
            if (!world.isRemote) {
                val blockpos1 = BlockPos(this)
                if (!onGround && collidedVertically) {
                    val iblockstate = world.getBlockState(blockpos1)
                    if (world.isAirBlock(
                                    BlockPos(posX, posY + 1.009999999776482582,
                                            posZ))) // Forge: Don't indent below.
                    {
                        if (BlockFalling.canFallThrough(world.getBlockState(
                                        BlockPos(posX, posY + 1.009999999776482582,
                                                posZ)))) {
                            collidedVertically = false
                            return
                        }
                    }
                    motionX *= 0.699999988079071
                    motionZ *= 0.699999988079071
                    motionY *= -0.5
                    if (iblockstate.block !== Blocks.PISTON_EXTENSION) {
                        setDead()
                        if (!dontSetBlock) {
                            if (world.mayPlace(block, blockpos1, true, EnumFacing.UP, null)
                                    && !BlockFalling.canFallThrough(world.getBlockState(blockpos1.up())) &&
                                    world.setBlockState(blockpos1, fallTile, 3)) {
                                if (block is BlockFalling) {
                                    block.onEndFalling(world, blockpos1,  //not used by this xd
                                            null, null)
                                }
                                if (tileEntityData != null
                                        && block is ITileEntityProvider) {
                                    val tileentity = world.getTileEntity(blockpos1)
                                    if (tileentity != null) {
                                        val nbttagcompound = tileentity.writeToNBT(NBTTagCompound())
                                        for (s in tileEntityData.keySet) {
                                            val nbtbase = tileEntityData.getTag(s)
                                            if ("x" != s && "y" != s && "z" != s) {
                                                nbttagcompound.setTag(s, nbtbase.copy())
                                            }
                                        }
                                        tileentity.readFromNBT(nbttagcompound)
                                        tileentity.markDirty()
                                    }
                                }
                            } else if (shouldDropItem && world.gameRules
                                            .getBoolean("doEntityDrops")) {
                                entityDropItem(ItemStack(block, 1, block.damageDropped(fallTile)), 0.0f)
                            }
                        }
                    }
                } else if (fallTime > 100 && (blockpos1.y < 1 || blockpos1.y > 256)
                        || fallTime > 600) {
                    if (shouldDropItem && world.gameRules.getBoolean("doEntityDrops")) {
                        entityDropItem(ItemStack(block, 1, block.damageDropped(fallTile)), 0.0f)
                    }
                    setDead()
                }
            }
        }
    }
}