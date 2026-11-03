package org.valkyrienskies.mod.client.gui;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.mod.common.annotation.LegacyContent;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;

@LegacyContent
public class TabValkyrienSkies extends CreativeTabs {
    public TabValkyrienSkies(String label) {
        super(label);
    }

    @Override
    public @NotNull ItemStack createIcon() {
        return new ItemStack(Item.getItemFromBlock(ValkyrienSkiesMod.INSTANCE.captainsChair));
    }
}
