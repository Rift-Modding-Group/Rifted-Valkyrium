package org.valkyrienskies.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.MoverType;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;
import org.valkyrienskies.mod.common.capability.ship_world.IShipWorld;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityCollisionInjector;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityCollisionInjector.IntermediateMovementVariableStorage;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityDraggable;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityMoveInjectionMethods;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.TransformedAABB;
import valkyrienwarfare.api.TransformType;

import java.util.List;

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

    @Unique
    @Nullable
    private ShipData boatSupportBeforeMove;

    @Shadow
    public abstract void move(MoverType type, double x, double y, double z);

    /**
     * fixes strange stuttering when sneaking while on top of a ship that's tilted
     */
    @NotNull
    @WrapOperation(
            method = "move",
            at = {
                    @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getCollisionBoxes(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/AxisAlignedBB;)Ljava/util/List;", ordinal = 0),
                    @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getCollisionBoxes(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/AxisAlignedBB;)Ljava/util/List;", ordinal = 1),
                    @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getCollisionBoxes(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/AxisAlignedBB;)Ljava/util/List;", ordinal = 2)
            },
            require = 3,
            allow = 3
    )
    private List<AxisAlignedBB> getSneakingSupportBoxes(
            @NotNull World world, @NotNull Entity entity, @NotNull AxisAlignedBB supportBox,
            @NotNull Operation<List<AxisAlignedBB>> original
    ) {
        List<AxisAlignedBB> collisionBoxes = original.call(world, entity, supportBox);
        if (!collisionBoxes.isEmpty()) return collisionBoxes;

        IShipWorld shipWorld = world.getCapability(VSCapabilityRegistry.VS_SHIP_WORLD, null);
        if (shipWorld == null) return collisionBoxes;

        AxisAlignedBB insetSupportBox = supportBox.grow(-0.2D, 0D, -0.2D);
        for (PhysicsObject ship : shipWorld.getManager().getPhysObjectsInAABB(supportBox)) {
            ShipTransform transform = ship.getShipTransformationManager().getCurrentTickTransform();
            AxisAlignedBB localSupportBox = new TransformedAABB(supportBox, transform, TransformType.GLOBAL_TO_SUBSPACE).getEnclosedAABB();
            if ((localSupportBox.maxX - localSupportBox.minX) * (localSupportBox.maxZ - localSupportBox.minZ) > 9898989D) {
                continue;
            }

            for (AxisAlignedBB localBlockBox : world.getCollisionBoxes(null, localSupportBox)) {
                AxisAlignedBB worldBlockBox = new TransformedAABB(localBlockBox, transform, TransformType.SUBSPACE_TO_GLOBAL).getEnclosedAABB();
                if (insetSupportBox.intersects(worldBlockBox)) {
                    return List.of(worldBlockBox);
                }
            }
        }
        return collisionBoxes;
    }

    /**
     * The goal of this injection is to adjust the arguments passed into move(). By adjusting the arguments we can add
     * collision between entities and ships.
     */
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void onEntityPreMove(MoverType type, double dx, double dy, double dz, CallbackInfo callbackInfo) {
        Entity thisEntity = (Entity) ((Object) this);
        if (thisEntity.world.isRemote && EntityDraggable.isUsingShipLocalMovement(thisEntity)) {
            this.alteredMovement = null;
            this.boatSupportBeforeMove = null;
            return;
        }

        if (this.didMinecraftInvokeMove) {
            this.boatSupportBeforeMove = EntityDraggable.getShipFromBoatBelow(thisEntity);
        }

        // Only run this code if Minecraft invoked move().
        if (!VSConfig.collisionTransparentEntitiesSet.contains(EntityList.getKey(thisEntity)) && didMinecraftInvokeMove) {
            this.alteredMovement = EntityMoveInjectionMethods.handleMove(type, dx, dy, dz, thisEntity);
            if (this.alteredMovement != null) {
                // We're about to invoke move, so set didMinecraftInvokeMove to false.
                this.didMinecraftInvokeMove = false;
                this.move(type, alteredMovement.dxyz.x(), alteredMovement.dxyz.y(), this.alteredMovement.dxyz.z());
                // Now our invocation of move has finished, so set didMinecraftInvokeMove back to true.
                this.didMinecraftInvokeMove = true;
                // Since we've already called move() in the code above, we must cancel the original invocation of move.
                // If we didn't cancel the original invocation then move() would be twice, which is obviously wrong.
                callbackInfo.cancel();
            }
        }
    }

    /**
     * The goal of this injection is to correctly setup {@link IEntityShipDraggable} for this Entity.
     * Specifically this code handles the last ship touched by the entity, as well as how many ticks ago that touch was.
     */
    @Inject(method = "move", at = @At("RETURN"))
    private void onEntityPostMove(CallbackInfo callbackInfo) {
        Entity thisEntity = (Entity) ((Object) this);
        if (thisEntity.world.isRemote && EntityDraggable.isUsingShipLocalMovement(thisEntity)) return;

        IEntityShipDraggable entityShipDraggable = thisEntity.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
        if (entityShipDraggable == null) return;

        //if alteredMovement isn't null then we're touching a ship.
        if (this.alteredMovement != null) {
            entityShipDraggable.setLastTouchedShip(this.alteredMovement.shipTouched);
            entityShipDraggable.setTicksSinceTouchedShip(0);
            entityShipDraggable.setTicksPartOfGround(0);
            EntityCollisionInjector.alterEntityMovementPost(thisEntity, this.alteredMovement);
        }
        else {
            //make sure that when on top of a boat on a ship, the game thinks
            //entity is still standing on the ship
            if (this.boatSupportBeforeMove != null) {
                entityShipDraggable.setLastTouchedShip(this.boatSupportBeforeMove);
                entityShipDraggable.setTicksSinceTouchedShip(0);
                entityShipDraggable.setTicksPartOfGround(0);
            }
            else if (this.collided) {
                entityShipDraggable.setLastTouchedShip(null);
                entityShipDraggable.setTicksSinceTouchedShip(0);
                entityShipDraggable.setTicksPartOfGround(entityShipDraggable.getTicksPartOfGround() + 1);
                entityShipDraggable.setAddedLinearVelocity(new Vector3d());
                entityShipDraggable.setAddedYawVelocity(0D);
            }
            else {
                int ticksPartOfGround = 0;
                if (entityShipDraggable.getLastTouchedShip() == null) ticksPartOfGround = entityShipDraggable.getTicksPartOfGround() + 1;
                entityShipDraggable.setTicksSinceTouchedShip(entityShipDraggable.getTicksSinceTouchedShip() + 1);
                entityShipDraggable.setTicksPartOfGround(ticksPartOfGround);
            }
        }
    }
}
