package org.valkyrienskies.mod.common.config;

import com.google.gson.JsonObject;
import net.minecraftforge.common.crafting.IConditionFactory;
import net.minecraftforge.common.crafting.JsonContext;

import java.util.function.BooleanSupplier;

/**
 * for use in disabling json recipes when legacy content is disabled
 * */
public class LegacyContentEnabledCondition implements IConditionFactory {
    @Override
    public BooleanSupplier parse(JsonContext context, JsonObject json) {
        return () -> !VSConfig.disableLegacyContent;
    }
}
