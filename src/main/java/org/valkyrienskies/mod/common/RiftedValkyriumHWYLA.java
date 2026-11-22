package org.valkyrienskies.mod.common;

import mcp.mobius.waila.api.*;
import net.minecraft.block.Block;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.physics.BlockPhysicsDetails;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.BlockMass;

import java.util.List;

@WailaPlugin(ValkyrienSkiesMod.MOD_ID)
public class RiftedValkyriumHWYLA implements IWailaPlugin {
    @Override
    public void register(IWailaRegistrar registrar) {
        if (VSConfig.showBlockMassesInDebug) return;

        registrar.registerBodyProvider(
                new IWailaDataProvider() {
                    @Override
                    @SideOnly(Side.CLIENT)
                    @NotNull
                    public List<String> getWailaBody(
                            @NotNull ItemStack itemStack, @NotNull List<String> tooltip,
                            @NotNull IWailaDataAccessor accessor, @NotNull IWailaConfigHandler config
                    ) {
                        if (ValkyrienUtils.getPhysoManagingBlock(accessor.getWorld(), accessor.getPosition()).isEmpty()) return tooltip;

                        BlockMass massCategory = BlockPhysicsDetails.getMassCategoryFromState(accessor.getBlockState());
                        tooltip.add(I18n.format("tooltip.valkyrienskies.mass_category", massCategory.toString(), massCategory.mass));
                        return tooltip;
                    }
                },
                Block.class
        );
    }
}
