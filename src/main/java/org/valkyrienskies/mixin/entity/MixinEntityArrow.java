package org.valkyrienskies.mixin.entity;

import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.util.math.RayTraceResult;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

import java.util.Optional;

@Mixin(EntityArrow.class)
public abstract class MixinEntityArrow {
    @Shadow
    protected boolean inGround;

    @Inject(method = "onHit", at = @At("RETURN"))
    private void attachArrowToHitShip(RayTraceResult rayTraceResult, CallbackInfo callbackInfo) {
        if (rayTraceResult.typeOfHit != RayTraceResult.Type.BLOCK || rayTraceResult.getBlockPos() == null) return;

        EntityArrow arrow = (EntityArrow) (Object) this;
        IEntityShipDraggable draggable = arrow.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
        if (draggable == null) return;

        Optional<PhysicsObject> hitShip = ValkyrienUtils.getPhysoManagingBlock(arrow.world, rayTraceResult.getBlockPos());
        if (hitShip.isPresent()) {
            draggable.setLastTouchedShip(hitShip.get().getShipData());
            draggable.setTicksSinceTouchedShip(0);
            draggable.setTicksPartOfGround(0);
            draggable.setAddedYawVelocity(0D);
        }
        else {
            draggable.setLastTouchedShip(null);
            draggable.setTicksSinceTouchedShip(0);
            draggable.setTicksPartOfGround(0);
            draggable.setAddedLinearVelocity(new Vector3d());
            draggable.setAddedYawVelocity(0D);
        }
    }

    @Inject(method = "onUpdate", at = @At("RETURN"))
    private void updateHitShipAttachment(CallbackInfo callbackInfo) {
        EntityArrow arrow = (EntityArrow) (Object) this;
        IEntityShipDraggable draggable = arrow.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
        if (draggable == null || draggable.getLastTouchedShip() == null) return;

        if (this.inGround) {
            draggable.setTicksSinceTouchedShip(0);
            draggable.setTicksPartOfGround(0);
        }
        else {
            draggable.setLastTouchedShip(null);
            draggable.setTicksSinceTouchedShip(0);
            draggable.setTicksPartOfGround(0);
            draggable.setAddedLinearVelocity(new Vector3d());
            draggable.setAddedYawVelocity(0D);
        }
    }
}
