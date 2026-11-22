package org.valkyrienskies.mod.common.item;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.ShipDataMethods;
import org.valkyrienskies.mod.common.util.BaseItem;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

@ParametersAreNonnullByDefault
public class ItemShipSizeLockStick extends BaseItem {
    public ItemShipSizeLockStick() {
        super("ship_size_lock_stick", false);
        this.setCreativeTab(ValkyrienSkiesMod.RV_DEBUG_TAG);
        this.setMaxStackSize(1);
    }

    @Override
    @NotNull
    public EnumActionResult onItemUseFirst(
            EntityPlayer player, World world, BlockPos pos, EnumFacing facing,
            float hitX, float hitY, float hitZ, EnumHand hand
    ) {
        if (world.isRemote) return EnumActionResult.SUCCESS;
        ShipData ship = ValkyrienUtils.getShipManagingBlock(world, pos).orElse(null);
        if (ship == null || !ShipDataMethods.toggleShipSizeLock(ship)) {
            player.sendStatusMessage(new TextComponentTranslation("message.valkyrienskies.size_lock.select_ship"), true);
            return EnumActionResult.FAIL;
        }
        String message = ship.shipBounds == null
                ? "message.valkyrienskies.size_lock.unlocked" : "message.valkyrienskies.size_lock.locked";
        player.sendStatusMessage(new TextComponentTranslation(message, ship.getName()), true);
        return EnumActionResult.SUCCESS;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(I18n.format("tooltip.valkyrienskies.size_lock.usage"));
    }
}
