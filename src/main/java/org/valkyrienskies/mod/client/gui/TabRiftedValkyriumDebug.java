package org.valkyrienskies.mod.client.gui;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;

public class TabRiftedValkyriumDebug extends CreativeTabs {
    public TabRiftedValkyriumDebug() {
        super("rifted_valkyrium_debug");
    }

    @Override
    @NotNull
    public ItemStack createIcon() {
        return new ItemStack(ValkyrienSkiesMod.INSTANCE.SHIP_ASSEMBLER_STICK);
    }
}
