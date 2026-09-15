package org.valkyrienskies.mixin.client;

import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.network.MessageOarShip;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.util.JOML;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

import java.util.Optional;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    /**
     * Adds ship rowing :D
     * */
    @Inject(method = "clickMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;resetCooldown()V"))
    private void tryOaringShip(CallbackInfo callbackInfo) {
        Minecraft minecraft = (Minecraft) (Object) this;
        ItemStack heldItem = minecraft.player.getHeldItemMainhand();
        if (heldItem.isEmpty() || minecraft.player.getCooldownTracker().hasCooldown(heldItem.getItem()) || VSConfig.oaringItems == null) {
            return;
        }

        //see if we can oar based on available items
        boolean canOar = false;
        for (String configuredItem : VSConfig.oaringItems) {
            if (ValkyrienUtils.itemStackMatchesString(heldItem, configuredItem)) {
                canOar = true;
                break;
            }
        }
        if (!canOar) return;

        //determine if we hittin water
        double reachDistance = minecraft.playerController.getBlockReachDistance();
        Vec3d eyePosition = minecraft.player.getPositionEyes(1f);
        Vec3d lookDirection = minecraft.player.getLook(1f);
        Vec3d traceEnd = eyePosition.add(
                lookDirection.x * reachDistance,
                lookDirection.y * reachDistance,
                lookDirection.z * reachDistance
        );
        RayTraceResult liquidHit = minecraft.world.rayTraceBlocks(eyePosition, traceEnd, true, false, false);
        if (liquidHit == null || liquidHit.typeOfHit != RayTraceResult.Type.BLOCK
                || minecraft.world.getBlockState(liquidHit.getBlockPos()).getMaterial() != Material.WATER
        ) {
            return;
        }

        //send
        ValkyrienSkiesMod.physWrapperNetwork.sendToServer(new MessageOarShip(
                minecraft.player.isSneaking(), lookDirection.x, lookDirection.z,
                liquidHit.hitVec.x, liquidHit.hitVec.y, liquidHit.hitVec.z
        ));
    }

    /**
     * This mixin fixes slabs not placing correctly on ships.
     */
    @Redirect(method = "rightClickMouse", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;processRightClickBlock(Lnet/minecraft/client/entity/EntityPlayerSP;Lnet/minecraft/client/multiplayer/WorldClient;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/EnumFacing;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/EnumHand;)Lnet/minecraft/util/EnumActionResult;"
    ))
    private EnumActionResult rightClickBlockProxy(PlayerControllerMP playerControllerMP, EntityPlayerSP player, WorldClient worldIn, BlockPos pos, EnumFacing direction, Vec3d vec, EnumHand hand) {
        // Check if this is for a ship
        final Optional<ShipData> shipDataOptional = ValkyrienUtils.getShipManagingBlock(worldIn, pos);
        if (shipDataOptional.isPresent()) {
            // This ray trace was in the ship, we're going to have to mess with the hit vector
            final ShipData shipData = shipDataOptional.get();
            final ShipTransform shipTransform = shipData.getShipTransform();

            // Put the hit vector in ship coordinates
            final Vector3d hitVecInLocal = JOML.convert(vec);
            shipTransform.transformPosition(hitVecInLocal, TransformType.GLOBAL_TO_SUBSPACE);
            final Vec3d moddedHitVec = JOML.toMinecraft(hitVecInLocal);
            return playerControllerMP.processRightClickBlock(player, worldIn, pos, direction, moddedHitVec, hand);
        }
        return playerControllerMP.processRightClickBlock(player, worldIn, pos, direction, vec, hand);
    }
}
