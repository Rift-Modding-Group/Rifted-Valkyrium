package org.valkyrienskies.mixin.mod.quark;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.entity_backup.ICapabilityEntityBackup;
import valkyrienwarfare.api.RiftedValkyriumAPI;
import valkyrienwarfare.api.TransformType;

@Pseudo
@Mixin(targets = "vazkii.quark.vanity.feature.SitInStairs", remap = false)
public abstract class MixinSitInStairs {
    /**
     * Measures seating reach in the player's current interaction coordinate frame.
     */
    @ModifyExpressionValue(
            method = "onInteract",
            at = @At(value = "NEW", target = "net/minecraft/util/math/Vec3d"),
            require = 1,
            remap = false
    )
    @NotNull
    private static Vec3d useSeatPositionInPlayerFrame(
            @NotNull Vec3d position, @Local(argsOnly = true) @NotNull PlayerInteractEvent.RightClickBlock event
    ) {
        ICapabilityEntityBackup backup = event.getEntityPlayer().getCapability(VSCapabilityRegistry.VS_ENTITY_BACKUP, null);
        if (backup != null && backup.hasBackupPosition()) return position;

        return RiftedValkyriumAPI.getPhysoManagingBlock(event.getWorld(), event.getPos())
                .map(ship -> ship.transformVector(position, TransformType.SUBSPACE_TO_GLOBAL))
                .orElse(position);
    }
}
