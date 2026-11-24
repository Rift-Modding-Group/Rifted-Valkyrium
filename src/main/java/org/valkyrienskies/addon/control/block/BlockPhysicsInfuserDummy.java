package org.valkyrienskies.addon.control.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import org.valkyrienskies.addon.control.util.BlockVSDirectional;

import javax.annotation.Nullable;

public class BlockPhysicsInfuserDummy extends BlockVSDirectional {
    public BlockPhysicsInfuserDummy() {
        super("physics_infuser_dummy", Material.WOOD, 0.0F, false);
        this.setHardness(2f);
        this.setResistance(5f);
    }

    @Override
    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (!worldIn.isRemote) {
            BlockPos parentPos = getParentPos(state, pos);
            IBlockState belowState = worldIn.getBlockState(parentPos);
            belowState.getBlock().onBlockActivated(worldIn, parentPos, belowState, playerIn, hand, side, hitX, hitY, hitZ);
        }
        return true;
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }

    @Override
    public void breakBlock(World worldIn, BlockPos pos, IBlockState state) {
        BlockPos parentPos = getParentPos(state, pos);
        super.breakBlock(worldIn, pos, state);
        if (worldIn.getBlockState(parentPos).getBlock() instanceof BlockPhysicsInfuser) {
            worldIn.setBlockToAir(parentPos);
        }
    }

    @Override
    public boolean removedByPlayer(IBlockState state, World world, BlockPos pos, EntityPlayer player, boolean willHarvest) {
        if (willHarvest) {
            this.onBlockHarvested(world, pos, state, player);
            return true;
        }
        return super.removedByPlayer(state, world, pos, player, false);
    }

    @Override
    public void harvestBlock(World world, EntityPlayer player, BlockPos pos, IBlockState state, @Nullable TileEntity tileEntity, ItemStack stack) {
        super.harvestBlock(world, player, pos, state, tileEntity, stack);
        world.setBlockToAir(pos);
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        BlockPos parentPos = getParentPos(state, pos);
        IBlockState parentState = world.getBlockState(parentPos);
        if (parentState.getBlock() instanceof BlockPhysicsInfuser parentBlock) {
            parentBlock.getDrops(drops, world, parentPos, parentState, fortune);
        }
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        BlockPos parentPos = getParentPos(state, pos);
        IBlockState parentState = world.getBlockState(parentPos);
        return parentState.getBlock().getPickBlock(parentState, target, world, parentPos, player);
    }

    private BlockPos getParentPos(IBlockState state, BlockPos pos) {
        return pos.offset(state.getValue(FACING));
    }
}
