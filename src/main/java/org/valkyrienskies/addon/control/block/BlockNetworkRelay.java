package org.valkyrienskies.addon.control.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockButton;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import org.valkyrienskies.addon.control.config.VSControlConfig;
import org.valkyrienskies.addon.control.item.ItemBaseWire;
import org.valkyrienskies.addon.control.tileentity.TileEntityNetworkRelay;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

@ParametersAreNonnullByDefault
public class BlockNetworkRelay extends BlockNodeComponentBasic {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");

    private static final AxisAlignedBB EAST = new AxisAlignedBB(0D / 16D, 5D / 16D, 5D / 16D,
        6D / 16D, 11D / 16D, 11D / 16D);
    private static final AxisAlignedBB WEST = new AxisAlignedBB(1D, 5D / 16D, 5D / 16D,
        10D / 16D, 11D / 16D, 11D / 16D);
    private static final AxisAlignedBB SOUTH = new AxisAlignedBB(5D / 16D, 5D / 16D, 0D / 16D,
        11D / 16D, 11D / 16D, 6D / 16D);
    private static final AxisAlignedBB NORTH = new AxisAlignedBB(5D / 16D, 5D / 16D, 1D,
        11D / 16D, 11D / 16D, 10D / 16D);
    private static final AxisAlignedBB UP = new AxisAlignedBB(5D / 16D, 0, 5D / 16D, 11D / 16D,
        6D / 16D, 11D / 16D);
    private static final AxisAlignedBB DOWN = new AxisAlignedBB(5D / 16D, 10D / 16D, 5D / 16D,
        11D / 16D, 1D, 11D / 16D);

    public BlockNetworkRelay() {
        super("network_relay", Material.IRON, 0.0F, true);
        this.setHardness(5.0F);
        this.setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
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
    @Nonnull
    public BlockFaceShape getBlockFaceShape(IBlockAccess worldIn, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    @Override
    @Nonnull
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        EnumFacing enumfacing = state.getValue(FACING);
        return switch (enumfacing) {
            case EAST -> EAST;
            case WEST -> WEST;
            case SOUTH -> SOUTH;
            case NORTH -> NORTH;
            case UP -> UP;
            case DOWN -> DOWN;
        };
    }

    @Override
    @Nonnull
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    /**
     * Called by ItemBlocks just before a block is actually set in the world, to allow for
     * adjustments to the IBlockstate
     */
    @Override
    @Nonnull
    public IBlockState getStateForPlacement(
            World worldIn, BlockPos pos, EnumFacing facing,
            float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer
    ) {
        return this.getDefaultState().withProperty(FACING, facing);
    }

    /**
     * Convert the given metadata into a BlockState for this block
     */
    @Override
    @Nonnull
    public IBlockState getStateFromMeta(int meta) {
        EnumFacing enumfacing = switch (meta & 7) {
            case 0 -> EnumFacing.DOWN;
            case 1 -> EnumFacing.EAST;
            case 2 -> EnumFacing.WEST;
            case 3 -> EnumFacing.SOUTH;
            case 4 -> EnumFacing.NORTH;
            default -> EnumFacing.UP;
        };

        return this.getDefaultState().withProperty(FACING, enumfacing);
    }

    /**
     * Convert the BlockState into the correct metadata value
     */
    @Override
    public int getMetaFromState(IBlockState state) {
        return switch (state.getValue(FACING)) {
            case DOWN -> 0;
            case EAST -> 1;
            case WEST -> 2;
            case SOUTH -> 3;
            case NORTH -> 4;
            default -> 5;
        };
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player,
        List<String> itemInformation, ITooltipFlag advanced) {
        itemInformation.add(TextFormatting.BLUE + I18n.format("tooltip.vs_control.network_relay", VSControlConfig.networkRelayLimit));
    }

    @Override
    @Nonnull
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileEntityNetworkRelay();
    }

    @Override
    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (worldIn.isRemote) return true;

        //block further interactions when dealing with wires
        ItemStack heldItem = playerIn.getHeldItem(hand);
        if (heldItem.getItem() instanceof ItemBaseWire) return false;

        //manage connections when dealing with shears.
        if (heldItem.getItem() instanceof ItemShears) {
            this.handleWireRemoval(worldIn, pos);
            return true;
        }

        return false;
    }

    @Override
    public void breakBlock(World worldIn, BlockPos pos, IBlockState state) {
        this.handleWireRemoval(worldIn, pos);
        super.breakBlock(worldIn, pos, state);
    }

    //believe it or not all the code below is copied from BlockLever
    @Override
    public void neighborChanged(IBlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos) {
        if (this.checkCanSurvive(worldIn, pos, state) && !BlockButton.canPlaceBlock(worldIn, pos, state.getValue(FACING))) {
            this.dropBlockAsItem(worldIn, pos, state, 0);
            worldIn.setBlockToAir(pos);
        }
    }

    private boolean checkCanSurvive(World worldIn, BlockPos pos, IBlockState state) {
        if (this.canPlaceBlockAt(worldIn, pos)) return true;
        else {
            this.dropBlockAsItem(worldIn, pos, state, 0);
            worldIn.setBlockToAir(pos);
            return false;
        }
    }
}
