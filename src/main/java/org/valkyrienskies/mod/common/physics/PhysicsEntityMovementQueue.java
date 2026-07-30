package org.valkyrienskies.mod.common.physics;

import net.minecraft.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.physics.bodies.IPhysicsEntityBody;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Transfers accumulated entity motion from the active physics backend to the game
 */
public class PhysicsEntityMovementQueue {
    @NotNull
    private final Map<Entity, PendingMovement> pendingMovements = new ConcurrentHashMap<>();
    @NotNull
    private final Map<Entity, MovementState> movementStates = new ConcurrentHashMap<>();
    @NotNull
    private final Map<Entity, SupportState> supportStates = new ConcurrentHashMap<>();
    @NotNull
    private final AtomicLong nextMovementEpoch = new AtomicLong();

    public void queue(@NotNull IPhysicsEntityBody body, @NotNull Entity entity, @NotNull Vector3dc displacement) {
        Matrix4dc movementTransform = new Matrix4d().translation(displacement.x(), displacement.y(), displacement.z());
        this.pendingMovements.compute(entity, (ignored, pending) -> {
            if (pending == null || pending.body != body) return new PendingMovement(body, new Matrix4d(movementTransform));
            return new PendingMovement(body, movementTransform.mul(pending.movementTransform, new Matrix4d()));
        });
    }

    public long register(@NotNull IPhysicsEntityBody body, @NotNull Entity entity) {
        long movementEpoch = this.nextMovementEpoch.incrementAndGet();
        this.movementStates.put(entity, new MovementState(body, movementEpoch, new Vector3d()));
        return movementEpoch;
    }

    public void remove(@NotNull IPhysicsEntityBody body, @NotNull Entity entity) {
        this.pendingMovements.computeIfPresent(entity, (ignored, pending) -> pending.body == body ? null : pending);
        this.movementStates.computeIfPresent(entity, (ignored, state) -> state.body == body ? null : state);

        this.setSupportingShip(body, entity, null);
    }

    public void setSupportingShip(@NotNull IPhysicsEntityBody body, @NotNull Entity entity, @Nullable UUID shipUuid) {
        if (shipUuid != null) this.supportStates.put(entity, new SupportState(body, shipUuid));
        else this.supportStates.computeIfPresent(entity, (ignored, state) -> state.body == body ? null : state);
    }

    public boolean isSupportedByShip(@NotNull Entity entity, @NotNull UUID shipUuid) {
        SupportState state = this.supportStates.get(entity);
        return state != null && state.shipUuid.equals(shipUuid);
    }

    /**
     * Captures how much backend movement has already been applied to an entity.
     */
    @NotNull
    public MovementMarker captureMovementMarker(@NotNull Entity entity) {
        MovementState state = this.movementStates.get(entity);
        if (state == null) return MovementMarker.NONE;
        return new MovementMarker(state.epoch, new Vector3d(state.appliedDisplacement));
    }

    /**
     * Applies all motion accumulated since the previous game-thread drain.
     */
    public void applyPendingMovements() {
        for (Map.Entry<Entity, PendingMovement> entry : this.pendingMovements.entrySet()) {
            Entity entity = entry.getKey();
            PendingMovement pending = entry.getValue();
            if (!this.pendingMovements.remove(entity, pending)) continue;

            Vector3d appliedDisplacement = pending.body.applyPendingMovement(pending.movementTransform);
            if (appliedDisplacement == null) continue;

            this.recordAppliedMovement(entity, pending.body, appliedDisplacement);
        }
    }

    /**
     * Carries supported entities after ships publish their game-tick transforms.
     */
    public void carrySupportedEntities(@NotNull IPhysObjectWorld physObjectWorld) {
        for (Map.Entry<Entity, SupportState> entry : this.supportStates.entrySet()) {
            Entity entity = entry.getKey();
            SupportState support = entry.getValue();
            if (this.supportStates.get(entity) != support || entity.isDead || entity.isRiding()) {
                continue;
            }

            PhysicsObject ship = physObjectWorld.getPhysObjectFromUUID(support.shipUuid);
            if (ship == null) continue;

            ShipTransform previous = ship.getShipTransformationManager().getPrevTickTransform();
            ShipTransform current = ship.getShipTransformationManager().getCurrentTickTransform();
            Matrix4d carrierTransform = ShipTransform.createTransform(previous, current);
            Vector3d appliedDisplacement = support.body.applyPendingMovement(carrierTransform);
            if (appliedDisplacement == null) continue;

            this.recordAppliedMovement(entity, support.body, appliedDisplacement);
        }
    }

    private void recordAppliedMovement(@NotNull Entity entity, @NotNull IPhysicsEntityBody body, @NotNull Vector3dc appliedDisplacement) {
        this.movementStates.computeIfPresent(
                entity,
                (ignoredEntity, state) -> {
                    if (state.body != body) return state;
                    return new MovementState(state.body, state.epoch, state.appliedDisplacement.add(appliedDisplacement, new Vector3d()));
                }
        );
    }

    public void clear() {
        this.pendingMovements.clear();
        this.movementStates.clear();
        this.supportStates.clear();
    }

    private record PendingMovement(@NotNull IPhysicsEntityBody body, @NotNull Matrix4dc movementTransform) {}

    private record MovementState(@NotNull IPhysicsEntityBody body, long epoch, @NotNull Vector3dc appliedDisplacement) {}

    private record SupportState(@NotNull IPhysicsEntityBody body, @NotNull UUID shipUuid) {}

    public record MovementMarker(long epoch, @NotNull Vector3dc appliedDisplacement) {
        public static final MovementMarker NONE = new MovementMarker(0L, new Vector3d());
    }
}
