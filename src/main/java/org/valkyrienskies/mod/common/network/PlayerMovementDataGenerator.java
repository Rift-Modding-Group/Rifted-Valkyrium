package org.valkyrienskies.mod.common.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import org.joml.Vector3d;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityShipMountData;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.util.JOML;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

import java.util.UUID;

public class PlayerMovementDataGenerator {
    /**
     * Only works on the client.
     */
    public static PlayerMovementData generatePlayerMovementDataForClient() {
        final EntityPlayerSP entityPlayer = Minecraft.getMinecraft().player;
        final EntityShipMountData anchoredMountData = ValkyrienUtils.getAnchoredMountShipAndPos(entityPlayer);

        //---send chair-mounted players using the anchored ship-local seat position---
        if (anchoredMountData.isMounted()) {
            final ShipData mountedShip = anchoredMountData.getMountedShip().getShipData();
            final Vector3d playerLookInLocal = JOML.convert(entityPlayer.getLook(1));
            mountedShip.getShipTransform().transformDirection(playerLookInLocal, TransformType.GLOBAL_TO_SUBSPACE);

            return new PlayerMovementData(
                    mountedShip.getUuid(),
                    0, 0,
                    JOML.convert(anchoredMountData.getMountPos()),
                    playerLookInLocal,
                    entityPlayer.onGround
            );
        }

        //---send non-mounted players using their last-touched ship-local position and look direction---
        else {
            IEntityShipDraggable draggable = entityPlayer.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
            if (draggable == null) {
                throw new RuntimeException("IEntityShipDraggable is not expected to be null!");
            }

            final ShipData lastTouchedShip = draggable.getLastTouchedShip();
            final UUID lastTouchedShipId = lastTouchedShip != null ? lastTouchedShip.getUuid() : null;
            final Vector3d playerPosInLocal = new Vector3d(entityPlayer.posX, entityPlayer.posY, entityPlayer.posZ);
            final Vector3d playerLookInLocal = JOML.convert(entityPlayer.getLook(1));
            final boolean onGround = entityPlayer.onGround;

            if (lastTouchedShip != null) {
                final ShipTransform shipTransform = lastTouchedShip.getShipTransform();
                shipTransform.transformPosition(playerPosInLocal, TransformType.GLOBAL_TO_SUBSPACE);
                shipTransform.transformDirection(playerLookInLocal, TransformType.GLOBAL_TO_SUBSPACE);
            }

            return new PlayerMovementData(
                    lastTouchedShipId,
                    draggable.getTicksSinceTouchedShip(),
                    draggable.getTicksPartOfGround(),
                    playerPosInLocal,
                    playerLookInLocal,
                    onGround
            );
        }
    }
}
