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
    private int rowingPlayerId;
    private double reachDistance;
    private double hitWaterX;
    private double hitWaterY;
    private double hitWaterZ;

    public MessageOarShip() {}

    public MessageOarShip(
            EntityPlayer rowingPlayer, double reachDistance,
            double hitWaterX, double hitWaterY, double hitWaterZ
    ) {
        this.rowingPlayerId = rowingPlayer.getEntityId();
        this.reachDistance = reachDistance;
        this.hitWaterX = hitWaterX;
        this.hitWaterY = hitWaterY;
        this.hitWaterZ = hitWaterZ;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.rowingPlayerId = buf.readInt();
        this.reachDistance = buf.readDouble();
        this.hitWaterX = buf.readDouble();
        this.hitWaterY = buf.readDouble();
        this.hitWaterZ = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.rowingPlayerId);
        buf.writeDouble(this.reachDistance);
        buf.writeDouble(this.hitWaterX);
        buf.writeDouble(this.hitWaterY);
        buf.writeDouble(this.hitWaterZ);
    }

    public static class Handler implements IMessageHandler<MessageOarShip, IMessage> {
        @Override
        public IMessage onMessage(MessageOarShip message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                EntityPlayer rowingPlayer = (EntityPlayer) player.getServerWorld().getEntityByID(message.rowingPlayerId);
                if (rowingPlayer == null) return;

                //get draggable info
                IEntityShipDraggable draggable = rowingPlayer.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
                if (draggable == null || draggable.getLastTouchedShip() == null
                        || draggable.getTicksSinceTouchedShip() >= VSConfig.ticksToStickToShip
                ) {
                    return;
                }

                //get look direction
                Vec3d lookDirection = rowingPlayer.getLook(1f);

                //create impulse to apply
                Vector3d impulse = new Vector3d(-lookDirection.x, 0D, -lookDirection.z);
                if (rowingPlayer.isSneaking()) impulse.mul(-1);
                if (impulse.lengthSquared() < 0.000001D) return;
                impulse.normalize(VSConfig.oaringImpulse);

                //get ship
                ShipData touchedShip = draggable.getLastTouchedShip();
                WorldServerShipManager shipManager = ValkyrienUtils.getServerShipManager(player.world);
                PhysicsObject physicsObject = shipManager.getPhysObjectFromUUID(touchedShip.getUuid());
                if (physicsObject == null || !physicsObject.isPhysicsReady() || !physicsObject.isPhysicsEnabled()) return;

                //set oaring cooldown
                if (VSConfig.oaringCooldownTicks > 0) {
                    rowingPlayer.getCooldownTracker().setCooldown(rowingPlayer.getHeldItemMainhand().getItem(), VSConfig.oaringCooldownTicks);
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
                    //get hit direction again
                    Vec3d eyePosition = rowingPlayer.getPositionEyes(1f);
                    Vec3d traceEnd = eyePosition.add(
                            lookDirection.x * message.reachDistance,
                            lookDirection.y * message.reachDistance,
                            lookDirection.z * message.reachDistance
                    );
                    RayTraceResult trueLiquidHit = player.getServerWorld().rayTraceBlocks(eyePosition, traceEnd, true, false, false);
                    if (trueLiquidHit == null || trueLiquidHit.typeOfHit != RayTraceResult.Type.BLOCK
                            || player.getServerWorld().getBlockState(trueLiquidHit.getBlockPos()).getMaterial() != Material.WATER
                    ) {
                        return;
                    }

                    //now add impulse
                    PhysicsCalculations calculations = physicsObject.getPhysicsCalculations();
                    ShipTransform transform = physicsObject.getShipTransformationManager().getCurrentPhysicsTransform();
                    Vector3d relativeHitPosition = new Vector3d(trueLiquidHit.hitVec.x, trueLiquidHit.hitVec.y, trueLiquidHit.hitVec.z);
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
