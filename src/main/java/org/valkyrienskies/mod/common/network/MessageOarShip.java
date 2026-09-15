package org.valkyrienskies.mod.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.joml.Vector3d;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.physics.PhysicsCalculations;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.ships.ship_world.WorldServerShipManager;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

public class MessageOarShip implements IMessage {
    private boolean isSneaking;
    private double lookDirectionX;
    private double lookDirectionZ;
    private double hitWaterX;
    private double hitWaterY;
    private double hitWaterZ;

    public MessageOarShip() {}

    public MessageOarShip(boolean isSneaking, double lookDirectionX, double lookDirectionZ, double hitWaterX, double hitWaterY, double hitWaterZ) {
        this.isSneaking = isSneaking;
        this.lookDirectionX = lookDirectionX;
        this.lookDirectionZ = lookDirectionZ;
        this.hitWaterX = hitWaterX;
        this.hitWaterY = hitWaterY;
        this.hitWaterZ = hitWaterZ;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.isSneaking = buf.readBoolean();
        this.lookDirectionX = buf.readDouble();
        this.lookDirectionZ = buf.readDouble();
        this.hitWaterX = buf.readDouble();
        this.hitWaterY = buf.readDouble();
        this.hitWaterZ = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(this.isSneaking);
        buf.writeDouble(this.lookDirectionX);
        buf.writeDouble(this.lookDirectionZ);
        buf.writeDouble(this.hitWaterX);
        buf.writeDouble(this.hitWaterY);
        buf.writeDouble(this.hitWaterZ);
    }

    public static class Handler implements IMessageHandler<MessageOarShip, IMessage> {
        @Override
        public IMessage onMessage(MessageOarShip message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                IEntityShipDraggable draggable = player.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
                if (draggable == null || draggable.getLastTouchedShip() == null
                        || draggable.getTicksSinceTouchedShip() >= VSConfig.ticksToStickToShip
                ) {
                    return;
                }

                //create impulse to apply
                Vector3d impulse = new Vector3d(-message.lookDirectionX, 0D, -message.lookDirectionZ);
                if (message.isSneaking) impulse.mul(-1);
                if (impulse.lengthSquared() < 0.000001D) return;
                impulse.normalize(VSConfig.oaringImpulse);

                //get ship
                ShipData touchedShip = draggable.getLastTouchedShip();
                WorldServerShipManager shipManager = ValkyrienUtils.getServerShipManager(player.world);
                PhysicsObject physicsObject = shipManager.getPhysObjectFromUUID(touchedShip.getUuid());
                if (physicsObject == null || !physicsObject.isPhysicsReady() || !physicsObject.isPhysicsEnabled()) return;

                //create position
                Vector3d hitPosition = new Vector3d(message.hitWaterX, message.hitWaterY, message.hitWaterZ);
                if (VSConfig.oaringCooldownTicks > 0) {
                    player.getCooldownTracker().setCooldown(player.getHeldItemMainhand().getItem(), VSConfig.oaringCooldownTicks);
                }

                //apply effects
                player.getServerWorld().spawnParticle(
                        EnumParticleTypes.WATER_SPLASH,
                        message.hitWaterX, message.hitWaterY, message.hitWaterZ,
                        10, 0.2D, 0.1D, 0.2D, 0D
                );
                player.getServerWorld().spawnParticle(
                        EnumParticleTypes.WATER_BUBBLE,
                        message.hitWaterX, message.hitWaterY, message.hitWaterZ,
                        5, 0.2D, 0.1D, 0.2D, 0D
                );
                player.world.playSound(
                        null,
                        message.hitWaterX, message.hitWaterY, message.hitWaterZ,
                        SoundEvents.ENTITY_BOAT_PADDLE_WATER, SoundCategory.PLAYERS, 0.9f, 1f
                );

                //send impulse
                shipManager.getPhysicsLoop().addScheduledTask(() -> {
                    PhysicsCalculations calculations = physicsObject.getPhysicsCalculations();
                    ShipTransform transform = physicsObject.getShipTransformationManager().getCurrentPhysicsTransform();
                    Vector3d relativeHitPosition = new Vector3d(hitPosition);
                    transform.transformPosition(relativeHitPosition, TransformType.GLOBAL_TO_SUBSPACE);
                    relativeHitPosition.sub(calculations.getPhysCenterOfMass());
                    transform.transformDirection(relativeHitPosition, TransformType.SUBSPACE_TO_GLOBAL);
                    calculations.addImpulseAtPoint(relativeHitPosition, impulse, new Vector3d());
                });
            });
            return null;
        }
    }
}
