package org.valkyrienskies.mixin.client.particle;

import net.minecraft.client.particle.ParticleItemPickup;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityDraggable;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

/**
 * Keeps the fixed start of an item pickup animation attached to the ship that carried the item.
 */
@Mixin(ParticleItemPickup.class)
public class MixinParticleItemPickup {
    @Unique
    @Nullable
    private Entity pickupItem;
    @Unique
    @Nullable
    private PhysicsObject pickupShip;
    @Unique
    private Vector3d localPickupPosition = new Vector3d();
    @Unique
    private Vector3d renderedPickupPosition = new Vector3d();
    @Unique
    private double originalItemX;
    @Unique
    private double originalItemY;
    @Unique
    private double originalItemZ;
    @Unique
    private boolean adjustedItemPosition;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void rememberShipPickupPosition(World world, Entity item, Entity target, float yOffset, CallbackInfo callbackInfo) {
        ShipData ship = EntityDraggable.getActiveShip(item);
        if (ship == null) return;

        IPhysObjectWorld physObjectWorld = ValkyrienUtils.getPhysObjWorld(world);
        if (physObjectWorld == null) return;

        PhysicsObject physicsObject = physObjectWorld.getPhysObjectFromUUID(ship.getUuid());
        if (physicsObject == null) return;

        this.pickupItem = item;
        this.pickupShip = physicsObject;
        this.localPickupPosition.set(item.posX, item.posY, item.posZ);
        physicsObject.getShipTransformationManager().getCurrentTickTransform()
                .transformPosition(this.localPickupPosition, TransformType.GLOBAL_TO_SUBSPACE);
    }

    @Inject(method = "renderParticle", at = @At("HEAD"))
    private void attachPickupStartToShip(
            BufferBuilder buffer, Entity camera, float partialTicks,
            float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ,
            CallbackInfo callbackInfo
    ) {
        if (this.pickupItem == null || this.pickupShip == null) return;

        this.originalItemX = this.pickupItem.posX;
        this.originalItemY = this.pickupItem.posY;
        this.originalItemZ = this.pickupItem.posZ;
        this.renderedPickupPosition.set(this.localPickupPosition);
        this.pickupShip.getShipTransformationManager().getRenderTransform().transformPosition(this.renderedPickupPosition, TransformType.SUBSPACE_TO_GLOBAL);
        this.pickupItem.posX = this.renderedPickupPosition.x;
        this.pickupItem.posY = this.renderedPickupPosition.y;
        this.pickupItem.posZ = this.renderedPickupPosition.z;
        this.adjustedItemPosition = true;
    }

    @Inject(method = "renderParticle", at = @At("RETURN"))
    private void restorePickupItemPosition(
            BufferBuilder buffer, Entity camera, float partialTicks,
            float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ,
            CallbackInfo callbackInfo
    ) {
        if (!this.adjustedItemPosition || this.pickupItem == null) return;

        this.pickupItem.posX = this.originalItemX;
        this.pickupItem.posY = this.originalItemY;
        this.pickupItem.posZ = this.originalItemZ;
        this.adjustedItemPosition = false;
    }
}
