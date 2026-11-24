package org.valkyrienskies.mixin.client.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

@Mixin(Entity.class)
public abstract class MixinEntityBrightness {
    @WrapOperation(
            method = "getBrightness",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getLightBrightness(Lnet/minecraft/util/math/BlockPos;)F"
            )
    )
    private float getShipAwareBrightness(World world, BlockPos pos, Operation<Float> original) {
        if (!world.isRemote || !ValkyrienUtils.notInFakeWorldBlacklist(world)) return original.call(world, pos);
        int light = world.getCombinedLight(pos, 0);
        int sky = Math.max(0, (light >> 20 & 15) - world.getSkylightSubtracted());
        int block = light >> 4 & 15;
        return world.provider.getLightBrightnessTable()[Math.max(sky, block)];
    }
}
