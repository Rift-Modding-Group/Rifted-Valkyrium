package org.valkyrienskies.addon.control.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import org.valkyrienskies.addon.control.util.BaseBlock;

import javax.annotation.Nonnull;

public class BlockShipWheel extends BaseBlock {

    private static final PropertyInteger modelId = PropertyInteger.create("modelid", 0, 15);

    public BlockShipWheel() {
        super("ship_helm_wheel", Material.WOOD, 0.0F, false);
        this.setHardness(5.0F);
        setDefaultState(getStateFromMeta(0));
    }

    @Override
    @Nonnull
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, modelId);
    }

    @Override
    @Nonnull
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(modelId, meta);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(modelId);
    }

}
