package org.valkyrienskies.addon.control.block.multiblocks;

import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.util.BaseBlock;
import org.valkyrienskies.mod.common.block.IBlockForceProvider;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

public class BlockRudderPart extends BaseBlock implements ITileEntityProvider, IBlockForceProvider {
    public BlockRudderPart() {
        super("rudder_part", Material.WOOD, 0.0F, true);
        this.setHardness(5.0F);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player,
        List<String> itemInformation,
        ITooltipFlag advanced) {
        itemInformation.add(TextFormatting.BLUE + I18n.format("tooltip.vs_control.rudder_part"));
        itemInformation.add(TextFormatting.BLUE + "" + TextFormatting.ITALIC + I18n.format("tooltip.vs_control.wrench_usage"));
    }

    private static Optional<Double> getRudderRotationDegrees(World world, BlockPos pos) {
        return Optional.empty();
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileEntityRudderPart();
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
    public void breakBlock(World worldIn, BlockPos pos, IBlockState state) {
        TileEntity tile = worldIn.getTileEntity(pos);
        if (tile instanceof TileEntityRudderPart tileRudderPart) {
            tileRudderPart.disassembleMultiblock();
        }
        super.breakBlock(worldIn, pos, state);
    }

    @Override
    public Vector3dc getBlockForceInShipSpace(World world, BlockPos pos, IBlockState state, PhysicsObject physicsObject) {
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileEntityRudderPart tileRudderPart)) return null;

        Vector3d force = tileRudderPart.calculateForceFromVelocity(physicsObject);
        if (force != null && force.lengthSquared() > 1) {
            return force;
        }
        return null;
    }

    @Override
    public Vector3dc getCustomBlockForcePosition(World world, BlockPos pos, IBlockState state, PhysicsObject physicsObject) {
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileEntityRudderPart tileRudderPart)) return null;
        return tileRudderPart.getForcePositionInShipSpace();
    }

    @Override
    public boolean shouldLocalForceBeRotated(World world, BlockPos pos, IBlockState state) {
        return true;
    }
}
