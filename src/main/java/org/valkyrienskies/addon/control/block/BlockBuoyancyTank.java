package org.valkyrienskies.addon.control.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import org.valkyrienskies.addon.control.config.VSControlConfig;
import org.valkyrienskies.mod.common.block.IBlockBuoyancyProvider;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.BaseBlock;

import javax.annotation.Nullable;
import java.util.List;

public class BlockBuoyancyTank extends BaseBlock implements IBlockBuoyancyProvider {
    public BlockBuoyancyTank() {
        super("buoyancy_tank", Material.IRON, 0f, true);
        this.setHardness(3f);
    }

    @Override
    public double getDisplacedWaterVolume(World world, BlockPos pos, IBlockState state, PhysicsObject physicsObject) {
        return Math.max(0D, VSControlConfig.buoyancyTankDisplacedVolume);
    }

    @Override
    public void addInformation(
            ItemStack stack,
            @Nullable World player,
            List<String> itemInformation,
            ITooltipFlag advanced
    ) {
        itemInformation.add(
                TextFormatting.GRAY + "" + TextFormatting.ITALIC + TextFormatting.BOLD
                        + I18n.format("tooltip.vs_control.buoyancy_tank", VSControlConfig.buoyancyTankDisplacedVolume)
        );
    }
}
