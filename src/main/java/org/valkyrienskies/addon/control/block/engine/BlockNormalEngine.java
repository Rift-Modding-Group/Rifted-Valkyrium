package org.valkyrienskies.addon.control.block.engine;

import net.minecraft.block.material.Material;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.RiftedValkyriumAPI;

public class BlockNormalEngine extends BlockAirshipEngineLore {

    public BlockNormalEngine(String name, Material mat, double enginePower, float hardness) {
        super(name, mat, enginePower, hardness);
    }

    @Override
    public String getEnginePowerTooltip() {
        return RiftedValkyriumAPI.formatMagnitude(this.enginePower);
    }
}
