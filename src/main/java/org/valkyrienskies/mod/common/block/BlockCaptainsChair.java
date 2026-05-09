package org.valkyrienskies.mod.common.block;

import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.entity.EntityShipMovementData;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.tileentity.TileEntityCaptainsChair;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

public class BlockCaptainsChair extends BlockPilotableBasic {

    public static final PropertyDirection FACING = BlockHorizontal.FACING;

    public BlockCaptainsChair() {
        super("captains_chair", Material.WOOD, 4.0F);
    }

    public double getChairYaw(IBlockState state, BlockPos pos) {
        EnumFacing enumFace = state.getValue(BlockCaptainsChair.FACING);
        return -enumFace.getHorizontalAngle() - 90;
    }

    @Override
    public boolean onBlockActivated(
            World worldIn, BlockPos pos, IBlockState state,
        EntityPlayer playerIn, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ
    ) {
        if (worldIn.isRemote) return true;

        Optional<PhysicsObject> physicsObject = ValkyrienUtils.getPhysoManagingBlock(worldIn, pos);
        if (physicsObject.isEmpty()) return true;

        TileEntity tileEntity = worldIn.getTileEntity(pos);
        if (!(tileEntity instanceof TileEntityCaptainsChair captainsChair)) return true;

        Vector3d playerPos = new Vector3d(playerIn.posX, playerIn.posY, playerIn.posZ);
        physicsObject.get()
                .getShipTransformationManager()
                .getCurrentTickTransform()
                .transformPosition(playerPos, TransformType.SUBSPACE_TO_GLOBAL);

        playerIn.posX = playerPos.x;
        playerIn.posY = playerPos.y;
        playerIn.posZ = playerPos.z;

        //-----mount the player to the chair-----
        Vector3dc localMountPos = this.getPlayerMountOffset(state, pos);
        ValkyrienUtils.fixEntityToShip(playerIn, localMountPos, physicsObject.get());

        //-----set piloting-----
        captainsChair.setPilotEntity(playerIn);
        physicsObject.get()
                .getShipTransformationManager()
                .getCurrentTickTransform()
                .transformPosition(playerPos, TransformType.GLOBAL_TO_SUBSPACE);

        playerIn.posX = playerPos.x;
        playerIn.posY = playerPos.y;
        playerIn.posZ = playerPos.z;

        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World player,
        List<String> itemInformation, ITooltipFlag advanced) {
        itemInformation.add(TextFormatting.ITALIC + "" + TextFormatting.BLUE + I18n
            .format("tooltip.valkyrienskies.captains_chair_1"));
    }

    private Vector3dc getPlayerMountOffset(IBlockState state, BlockPos pos) {
        EnumFacing facing = state.getValue(FACING);
        return switch (facing) {
            case NORTH -> new Vector3d(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.6D);
            case SOUTH -> new Vector3d(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.4D);
            case WEST -> new Vector3d(pos.getX() + 0.6D, pos.getY(), pos.getZ() + 0.5D);
            case EAST -> new Vector3d(pos.getX() + 0.4D, pos.getY(), pos.getZ() + 0.5D);
            default -> new Vector3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        };
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileEntityCaptainsChair();
    }

    @Override
    public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing facing,
        float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        return this.getDefaultState().withProperty(FACING,
            placer.isSneaking() ? placer.getHorizontalFacing().getOpposite()
                : placer.getHorizontalFacing());
    }

    public IBlockState onBlockPlaced(World worldIn, BlockPos pos, EnumFacing facing, float hitX,
        float hitY, float hitZ, int meta, EntityLivingBase placer) {
        EnumFacing facingHorizontal = placer.getHorizontalFacing();

        if (!placer.isSneaking()) {
            facingHorizontal = facingHorizontal.getOpposite();
        }

        return this.getDefaultState().withProperty(FACING, facingHorizontal);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        EnumFacing enumfacing = EnumFacing.byIndex(meta);
        if (enumfacing.getAxis() == EnumFacing.Axis.Y) {
            enumfacing = EnumFacing.NORTH;
        }
        return this.getDefaultState().withProperty(FACING, enumfacing);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        int i = state.getValue(FACING)
            .getIndex();
        return i;
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
    @Nullable
    public AxisAlignedBB getCollisionBoundingBox(IBlockState blockState, IBlockAccess worldIn, BlockPos pos) {
        return null;
    }

}