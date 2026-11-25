package org.valkyrienskies.mod.common.item;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.BaseItem;
import org.valkyrienskies.mod.common.util.VSMath;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.RiftedValkyriumAPI;
import valkyrienwarfare.api.ShipBuildOptions;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

@ParametersAreNonnullByDefault
public class ItemShipAssemblerStick extends BaseItem {
    private static final String SELECTION_TAG = "AssemblySelection";

    public ItemShipAssemblerStick() {
        super("ship_assembler_stick", false);
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
        if (!(world instanceof WorldServer serverWorld)) return EnumActionResult.FAIL;

        ItemStack stack = player.getHeldItem(hand);
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) tag = new NBTTagCompound();
        if (player.isSneaking()) {
            tag.removeTag(SELECTION_TAG);
            stack.setTagCompound(tag);
            player.sendStatusMessage(new TextComponentTranslation("message.valkyrienskies.assembler.cleared"), true);
            return EnumActionResult.SUCCESS;
        }

        PhysicsObject ship = ValkyrienUtils.getPhysoManagingBlock(world, pos).orElse(null);

        //---disassembly---
        if (ship != null) {
            tag.removeTag(SELECTION_TAG);
            stack.setTagCompound(tag);
            ship.setShipAligningToGrid(true);
            ship.setDeconstruct(true);
            return EnumActionResult.SUCCESS;
        }

        //---assembly---
        NBTTagCompound selection = tag.getCompoundTag(SELECTION_TAG);
        if (!selection.hasKey("Position", 4) || selection.getInteger("Dimension") != world.provider.getDimension()) {
            selection.setLong("Position", pos.toLong());
            selection.setInteger("Dimension", world.provider.getDimension());
            tag.setTag(SELECTION_TAG, selection);
            stack.setTagCompound(tag);
            player.sendStatusMessage(new TextComponentTranslation("message.valkyrienskies.assembler.selected"), true);
            return EnumActionResult.SUCCESS;
        }

        BlockPos firstPosition = BlockPos.fromLong(selection.getLong("Position"));
        AxisAlignedBB bounds = new AxisAlignedBB(firstPosition).union(new AxisAlignedBB(pos));
        if (bounds.maxX - bounds.minX > 4096D || bounds.maxZ - bounds.minZ > 4096D) {
            player.sendStatusMessage(new TextComponentTranslation("message.valkyrienskies.assembler.too_large"), true);
            return EnumActionResult.FAIL;
        }
        BlockPos minimum = new BlockPos(bounds.minX, bounds.minY, bounds.minZ);
        BlockPos maximum = new BlockPos(bounds.maxX - 1D, bounds.maxY - 1D, bounds.maxZ - 1D);
        if (!serverWorld.isAreaLoaded(minimum, maximum)) {
            player.sendStatusMessage(new TextComponentTranslation("message.valkyrienskies.assembler.unloaded"), true);
            return EnumActionResult.FAIL;
        }
        BlockPos assemblyPosition = new BlockPos(VSMath.getCenter(bounds));
        if (RiftedValkyriumAPI.createNewShip(serverWorld, assemblyPosition, bounds, new ShipBuildOptions().setPhysicsEnabled(true)) == null) {
            return EnumActionResult.FAIL;
        }
        tag.removeTag(SELECTION_TAG);
        stack.setTagCompound(tag);
        player.sendStatusMessage(new TextComponentTranslation("message.valkyrienskies.assembler.queued"), true);
        return EnumActionResult.SUCCESS;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(I18n.format("tooltip.valkyrienskies.assembler.usage"));
    }
}
