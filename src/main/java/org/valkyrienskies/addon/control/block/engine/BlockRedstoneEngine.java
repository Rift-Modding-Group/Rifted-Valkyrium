package org.valkyrienskies.addon.control.block.engine;

import net.minecraft.block.material.Material;
import org.valkyrienskies.addon.control.config.VSControlConfig;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

public class BlockRedstoneEngine extends BlockAirshipEngineLore {

    public BlockRedstoneEngine() {
        super("redstone", Material.REDSTONE_LIGHT, VSControlConfig.ENGINE_THRUST.redstoneEngineThrust, 7.0F);
    }

    @Override
    public String getEnginePowerTooltip() {
        return ValkyrienUtils.formatMagnitude(this.enginePower)
                + " * redstone power level";
    }

}
