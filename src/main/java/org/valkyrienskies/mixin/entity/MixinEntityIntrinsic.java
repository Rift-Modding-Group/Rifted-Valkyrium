package org.valkyrienskies.mixin.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.MoverType;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.entity.EntityShipMovementData;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityCollisionInjector;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityCollisionInjector.IntermediateMovementVariableStorage;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityMoveInjectionMethods;

@Mixin(value = Entity.class, priority = 1)
public abstract class MixinEntityIntrinsic {

    @Shadow
    public double posX;
    @Shadow
    public double posY;
    @Shadow
    public double posZ;
    @Shadow
    public World world;
    @Shadow
    public boolean collided;

    // Used to remember alteredMovement, so that it can be passed from changeMoveArgs() to postMove()
    private IntermediateMovementVariableStorage alteredMovement = null;
    // We only want to run onEntityPreMove() code when Minecraft calls Entity.move(), not when we call it ourselves.
    // We use this boolean to keep track of when we've called move() ourselves.
    private boolean didMinecraftInvokeMove = true;

    @Shadow
    public abstract void move(MoverType type, double x, double y, double z);

    /**
     * The goal of this injection is to adjust the arguments passed into move(). By adjusting the arguments we can add
     * collision between entities and ships.
     */
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void onEntityPreMove(MoverType type, double dx, double dy, double dz, CallbackInfo callbackInfo) {
        Entity thisEntity = (Entity) ((Object) this);

        // Only run this code if Minecraft invoked move().
        if (!VSConfig.collisionTransparentEntitiesSet.contains(EntityList.getKey(thisEntity)) && didMinecraftInvokeMove) {
            alteredMovement = EntityMoveInjectionMethods
                .handleMove(type, dx, dy, dz, thisEntity);
            if (alteredMovement != null) {
                // We're about to invoke move, so set didMinecraftInvokeMove to false.
                didMinecraftInvokeMove = false;
                this.move(type, alteredMovement.dxyz.x(), alteredMovement.dxyz.y(),
                    alteredMovement.dxyz.z());
                // Now our invocation of move has finished, so set didMinecraftInvokeMove back to true.
                didMinecraftInvokeMove = true;
                // Since we've already called move() in the code above, we must cancel the original invocation of move.
                // If we didn't cancel the original invocation then move() would be twice, which is obviously wrong.
                callbackInfo.cancel();
            }
        }
    }

    /**
     * Updates the ship-contact portion of {@link IEntityShipDraggable} for this entity.
     * This metadata is still needed by ship-aware rendering and player networking;
     * it no longer applies ship displacement.
     */
    @Inject(method = "move", at = @At("RETURN"))
    private void onEntityPostMove(CallbackInfo callbackInfo) {
        Entity thisEntity = (Entity) ((Object) this);
        IEntityShipDraggable entityShipDraggable = thisEntity.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
        if (entityShipDraggable == null) return;

        final EntityShipMovementData entityShipMovementData = entityShipDraggable.getEntityShipMovementData();
        if (entityShipMovementData == null) return;

        if (this.alteredMovement != null) {
            // If alteredMovement isn't null then we're touching a ship.
            entityShipMovementData.setLastTouchedShip(alteredMovement.shipTouched);
            entityShipMovementData.setTicksSinceTouchedShip(0);
            entityShipMovementData.setTicksPartOfGround(0);
            entityShipMovementData.setStandingOnShip(alteredMovement.standingOnShip);
            EntityCollisionInjector.alterEntityMovementPost(thisEntity, alteredMovement);
        }
        else {
            if (this.collided) {
                // If we collided and alteredMovement is null, then we're touching the ground.
                entityShipMovementData.setLastTouchedShip(null);
                entityShipMovementData.setTicksSinceTouchedShip(0);
                entityShipMovementData.setTicksPartOfGround(
                        entityShipMovementData.getTicksPartOfGround() + 1
                );
                entityShipMovementData.setStandingOnShip(false);
            }
            else {
                // If we're not collided and alteredMovement is null, then we're in the air.
                final int newTicksPartOfGround;
                if (entityShipMovementData.getLastTouchedShip() != null) {
                    newTicksPartOfGround = 0;
                }
                else {
                    newTicksPartOfGround = entityShipMovementData.getTicksPartOfGround() + 1;
                }
                entityShipMovementData.setTicksSinceTouchedShip(
                        entityShipMovementData.getTicksSinceTouchedShip() + 1
                );
                entityShipMovementData.setTicksPartOfGround(newTicksPartOfGround);
                entityShipMovementData.setStandingOnShip(false);
            }
        }
    }
}
