package org.valkyrienskies.addon.control.item;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import org.valkyrienskies.addon.control.block.BlockRotationAxle;
import org.valkyrienskies.addon.control.block.multiblocks.ITileEntityMultiblockPart;
import org.valkyrienskies.addon.control.block.torque.TileEntityRotationAxle;
import org.valkyrienskies.addon.control.config.VSControlConfig;
import org.valkyrienskies.addon.control.tileentity.TileEntityGearbox;
import org.valkyrienskies.mod.common.util.BaseItem;

import javax.annotation.Nullable;
import java.util.List;

public class ItemVSWrench extends BaseItem {
    private EnumWrenchMode mode = EnumWrenchMode.CONSTRUCT;

    public ItemVSWrench() {
		super("vs_wrench", true);
        this.setMaxStackSize(1);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player,
        List<String> itemInformation,
        ITooltipFlag advanced) {
        if (VSControlConfig.wrenchModeless) {
            itemInformation.add(TextFormatting.BLUE + I18n.format("tooltip.vs_control.wrench_toggle"));
        } else {
            itemInformation.add(TextFormatting.BLUE + I18n.format("tooltip.vs_control.wrench." + this.mode.toString()));

            GameSettings settings = Minecraft.getMinecraft().gameSettings;
            String useItemKey = settings.keyBindUseItem.getDisplayName();
            String sneakKey = settings.keyBindSneak.getDisplayName();

            itemInformation.add(TextFormatting.GREEN + "" + TextFormatting.ITALIC +
                    I18n.format("tooltip.vs_control.wrench_modes", sneakKey, useItemKey));
        }
    }

    // Construct potential multiblock if set to construct mode.
    // Otherwise, try to deconstruct a multiblock.
    @Override
    public EnumActionResult onItemUse(
            EntityPlayer player, World worldIn, BlockPos pos, EnumHand hand,
            EnumFacing facing, float hitX, float hitY, float hitZ
    ) {
        if (worldIn.isRemote) {
            return EnumActionResult.SUCCESS;
        }

        if (player.isSneaking() && !VSControlConfig.wrenchModeless) {
            this.mode = EnumWrenchMode.values()[(this.mode.ordinal() + 1) % EnumWrenchMode.values().length]; // Switch to the next mode
            player.sendMessage(new TextComponentString(
                TextFormatting.BLUE + I18n.format("tooltip.vs_control.wrench.switched." + this.mode.toString()))); // Say in chat
            return EnumActionResult.SUCCESS;
        }

        TileEntity blockTile = worldIn.getTileEntity(pos);
        boolean shouldConstruct = this.mode == EnumWrenchMode.CONSTRUCT || VSControlConfig.wrenchModeless;
        boolean shouldDeconstruct = this.mode == EnumWrenchMode.DECONSTRUCT || VSControlConfig.wrenchModeless;
        if (blockTile instanceof ITileEntityMultiblockPart part) {
            shouldConstruct = shouldConstruct && !part.isPartOfAssembledMultiblock();
            shouldDeconstruct = shouldDeconstruct && part.isPartOfAssembledMultiblock();
        }
        else if (blockTile instanceof TileEntityGearbox || blockTile instanceof TileEntityRotationAxle) {
            shouldConstruct = true;
        }
        else {
            return EnumActionResult.PASS;
        }

        if (shouldConstruct) {
            if (blockTile instanceof ITileEntityMultiblockPart part) {
                if (part.attemptToAssembleMultiblock(worldIn, pos, facing)) {
                    return EnumActionResult.SUCCESS;
                }
            }
            else if (blockTile instanceof TileEntityGearbox gearbox) {
                gearbox.setInputFacing(player.isSneaking() ? facing.getOpposite() : facing);
                return EnumActionResult.SUCCESS;
            }
            else if (blockTile instanceof TileEntityRotationAxle rotationAxle) {
                IBlockState state = worldIn.getBlockState(pos);
                worldIn.setBlockState(pos, state.withProperty(BlockRotationAxle.AXIS, facing.getAxis()));
                rotationAxle.setAxleAxis(facing.getAxis());
                return EnumActionResult.SUCCESS;
            }
        }
        else if (shouldDeconstruct) {
            ((ITileEntityMultiblockPart) blockTile).disassembleMultiblock();
            return EnumActionResult.SUCCESS;
        }

        return EnumActionResult.PASS;
    }

    public EnumWrenchMode getMode() {
        return this.mode;
    }
}
