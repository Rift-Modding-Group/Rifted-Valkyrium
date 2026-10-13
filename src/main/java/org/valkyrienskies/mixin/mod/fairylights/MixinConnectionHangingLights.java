package org.valkyrienskies.mixin.mod.fairylights;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.pau101.fairylights.server.fastener.Fastener;
import com.pau101.fairylights.server.fastener.connection.type.hanginglights.ConnectionHangingLights;
import com.pau101.fairylights.server.fastener.connection.type.hanginglights.Light;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

/**
 * Allow hanging lights from Fairy Lights on ships to emit light :D
 */
@Pseudo
@Mixin(targets = "com.pau101.fairylights.server.fastener.connection.type.hanginglights.ConnectionHangingLights", remap = false)
public abstract class MixinConnectionHangingLights {
    @Unique
    @Nullable
    private PhysicsObject valkyrienskies$lightShip;

    /**
     * Fairy Lights normally disables block lighting for "dynamic" connections.
     * This should override it as long as both points of the connection are on a ship.
     */
    @WrapOperation(
            method = {"onUpdateLate", "createFeature"},
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/pau101/fairylights/server/fastener/connection/type/hanginglights/ConnectionHangingLights;isDynamic()Z"
            )
    )
    private boolean enableLightingOnSameShip(ConnectionHangingLights connection, Operation<Boolean> original) {
        boolean dynamic = original.call(connection);
        this.valkyrienskies$lightShip = null;
        if (!dynamic) return false;

        this.valkyrienskies$lightShip = valkyrienskies$getConnectionShip(connection);
        return this.valkyrienskies$lightShip == null;
    }

    /**
     * Converts light source position into ship space. The connection's geometry remains
     * in world space so Fairy Lights can continue rendering the moving strand.
     */
    @WrapOperation(
            method = {"onUpdateLate", "createFeature"},
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/pau101/fairylights/server/fastener/connection/type/hanginglights/Light;getAbsolutePoint(Lcom/pau101/fairylights/server/fastener/Fastener;)Lnet/minecraft/util/math/Vec3d;"
            )
    )
    private Vec3d useShipLocalLightPosition(Light light, Fastener<?> fastener, Operation<Vec3d> original) {
        Vec3d globalPosition = original.call(light, fastener);
        PhysicsObject ship = this.valkyrienskies$lightShip;
        if (ship == null) return globalPosition;

        return ship.transformVector(globalPosition, TransformType.GLOBAL_TO_SUBSPACE);
    }

    @Unique
    @Nullable
    private static PhysicsObject valkyrienskies$getConnectionShip(@NotNull ConnectionHangingLights connection) {
        World world = connection.getWorld();
        Fastener<?> source = connection.getFastener();
        PhysicsObject sourceShip = ValkyrienUtils.getPhysoManagingBlock(world, source.getPos()).orElse(null);
        if (sourceShip == null || !connection.getDestination().isLoaded(world)) return null;

        Fastener<?> destination = connection.getDestination().get(world);
        if (destination == null) return null;

        PhysicsObject destinationShip = ValkyrienUtils.getPhysoManagingBlock(world, destination.getPos()).orElse(null);
        if (sourceShip != destinationShip) return null;

        return sourceShip;
    }
}