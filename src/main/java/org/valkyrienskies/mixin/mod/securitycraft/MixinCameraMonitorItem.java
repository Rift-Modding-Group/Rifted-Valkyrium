package org.valkyrienskies.mixin.mod.securitycraft;

import net.geforcemods.securitycraft.blockentities.SecurityCameraBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.compat.SecurityCraftCameraMonitorRelocations;

@Pseudo
@Mixin(targets = "net.geforcemods.securitycraft.items.CameraMonitorItem", remap = false)
public abstract class MixinCameraMonitorItem extends Item {
    @Override
    public void onUpdate(@NotNull ItemStack stack, @NotNull World world, @NotNull Entity entity, int slot, boolean selected) {
        super.onUpdate(stack, world, entity, slot, selected);
        if (world.isRemote || !stack.hasTagCompound()) return;
        SecurityCraftCameraMonitorRelocations relocations = SecurityCraftCameraMonitorRelocations.get(world);
        if (relocations != null) relocations.updateMonitor(stack);
    }

    @Inject(method = "onItemUse", remap = true, at = @At("HEAD"))
    private void updateLinksBeforeBinding(
            EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing,
            float hitX, float hitY, float hitZ, CallbackInfoReturnable<EnumActionResult> callbackInfo
    ) {
        if (world.isRemote || !(world.getTileEntity(pos) instanceof SecurityCameraBlockEntity)) return;
        ItemStack stack = player.getHeldItem(hand);
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        SecurityCraftCameraMonitorRelocations relocations = SecurityCraftCameraMonitorRelocations.get(world);
        if (relocations != null) relocations.updateMonitor(stack);
    }
}
