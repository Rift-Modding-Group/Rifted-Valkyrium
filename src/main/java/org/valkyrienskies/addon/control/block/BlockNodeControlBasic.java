package org.valkyrienskies.addon.control.block;

import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.valkyrienskies.addon.control.item.ItemBaseWire;
import org.valkyrienskies.addon.control.tileentity.ITileEntityControlNode;
import org.valkyrienskies.mod.common.util.BaseBlock;

public abstract class BlockNodeControlBasic extends BaseBlock implements ITileEntityProvider {
    public BlockNodeControlBasic(String name, Material mat, float hardness) {
        super(name, mat, 0.0F, true);
        this.setHardness(hardness);
    }

    @Override
    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (worldIn.isRemote) return true;
        //no need to awkwardly sneak when attaching a node to a node controller
        if (playerIn.getHeldItem(hand).getItem() instanceof ItemBaseWire) return false;
        TileEntity tileIn = worldIn.getTileEntity(pos);
        if (tileIn instanceof ITileEntityControlNode controlNode) {
            controlNode.setUserEntity(playerIn);
        }
        return true;
    }
}
