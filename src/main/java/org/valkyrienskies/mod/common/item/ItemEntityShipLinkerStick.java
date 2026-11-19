package org.valkyrienskies.mod.common.item;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
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
import org.valkyrienskies.mod.common.util.BaseItem;
import valkyrienwarfare.api.IShipData;
import valkyrienwarfare.api.RiftedValkyriumAPI;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

@ParametersAreNonnullByDefault
public class ItemEntityShipLinkerStick extends BaseItem {
    public static final String SELECTED_SHIP = "SelectedShip";
    public static final String SELECTED_DIMENSION = "SelectedDimension";

    public ItemEntityShipLinkerStick() {
        super("entity_ship_linker_stick", false);
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
        IShipData ship = RiftedValkyriumAPI.getShipManagingBlock(world, pos).orElse(null);
        if (ship == null) {
            player.sendStatusMessage(new TextComponentTranslation("message.valkyrienskies.linker.select_ship"), true);
            return EnumActionResult.FAIL;
        }
        ItemStack stack = player.getHeldItem(hand);
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) tag = new NBTTagCompound();
        tag.setUniqueId(SELECTED_SHIP, ship.getUuid());
        tag.setInteger(SELECTED_DIMENSION, world.provider.getDimension());
        stack.setTagCompound(tag);
        player.sendStatusMessage(new TextComponentTranslation("message.valkyrienskies.linker.selected", ship.getName()), true);
        return EnumActionResult.SUCCESS;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(I18n.format("tooltip.valkyrienskies.linker.usage"));
    }
}