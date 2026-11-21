package org.valkyrienskies.mod.common.capability.anchored_mount;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.entity.EntityMountable;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.ShipLocalEntityMovementData;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityDraggable;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import org.valkyrienskies.mod.common.util.multithreaded.CalledFromWrongThreadException;
import valkyrienwarfare.api.TransformType;

import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ImplCapabilityShipAnchoredMount implements IShipAnchoredMount {
    private static final Set<Class<? extends Entity>> alignedEntityClasses = ConcurrentHashMap.newKeySet();

    public static void registerAlignedEntity(@NotNull Class<? extends Entity> entityClass) {
        alignedEntityClasses.add(Objects.requireNonNull(entityClass, "entityClass"));
    }

    public static boolean isAlignedEntity(@NotNull Class<? extends Entity> entityClass) {
        return alignedEntityClasses.stream().anyMatch(registered -> registered.isAssignableFrom(entityClass));
    }

    private boolean anchoredToShip = false;
    @NotNull
    private Vec3d localMountPos = Vec3d.ZERO;
    @NotNull
    private BlockPos localAnchorBlock = BlockPos.ORIGIN;

    @Override
    public boolean isAnchoredToShip() {
        return this.anchoredToShip;
    }

    @Override
    public @NotNull Vec3d getLocalMountPos() {
        return this.localMountPos;
    }

    @Override
    public @NotNull BlockPos getLocalAnchorBlock() {
        return this.localAnchorBlock;
    }

    @Override
    public void setAnchorMountData(@NotNull Vec3d localMountPos, @NotNull BlockPos localAnchorBlock) {
        this.localMountPos = localMountPos;
        this.localAnchorBlock = localAnchorBlock.toImmutable();
        this.anchoredToShip = true;
    }

    @Override
    public boolean tryAnchorMount(@NotNull Entity entity) {
        if (entity instanceof EntityMountable || entity.isRiding()) return false;
        if (this.anchoredToShip) return true;

        boolean alignedEntity = isAlignedEntity(entity.getClass());
        ResourceLocation entityId = EntityList.getKey(entity);
        if (!alignedEntity && (entityId == null || VSConfig.sittableBlockEntityIDsSet == null
                || !VSConfig.sittableBlockEntityIDsSet.contains(entityId))) return false;

        try {
            Vec3d rawEntityPos = new Vec3d(entity.posX, entity.posY, entity.posZ);

            //fast path for new seats spawned in shipyard/subspace coordinates.
            BlockPos possibleLocalAnchor = new BlockPos(entity.posX, entity.posY, entity.posZ);
            Optional<PhysicsObject> shipAtLocalPos = ValkyrienUtils.getPhysoManagingBlock(entity.world, possibleLocalAnchor);
            if (shipAtLocalPos.isPresent()) {
                if (shipAtLocalPos.get().getShipData().isPeggedTo(entity)) return false;
                setAnchorMountData(rawEntityPos, possibleLocalAnchor);
                return true;
            }

            if (alignedEntity) {
                ShipData carryingShip = EntityDraggable.getActiveShip(entity);
                if (carryingShip == null) return false;

                Vec3d localPosition = carryingShip.getShipTransform().transform(
                        rawEntityPos, TransformType.GLOBAL_TO_SUBSPACE);
                this.setAnchorMountData(localPosition, new BlockPos(localPosition));
                if (!entity.world.isRemote) {
                    float localYaw = (float) ShipLocalEntityMovementData.transformYaw(
                            carryingShip.getShipTransform(), entity.rotationYaw, TransformType.GLOBAL_TO_SUBSPACE);
                    float yawChange = localYaw - entity.rotationYaw;
                    entity.rotationYaw = localYaw;
                    entity.prevRotationYaw += yawChange;
                    if (entity instanceof EntityLivingBase living) {
                        living.rotationYawHead += yawChange;
                        living.prevRotationYawHead += yawChange;
                        living.renderYawOffset += yawChange;
                        living.prevRenderYawOffset += yawChange;
                    }
                }
                return true;
            }

            // Client/network path: infer the owning ship if this entity is already in global space.
            IPhysObjectWorld physObjectWorld = ValkyrienUtils.getPhysObjWorld(entity.world);
            if (physObjectWorld == null) return false;

            AxisAlignedBB searchBox = new AxisAlignedBB(
                    entity.posX, entity.posY, entity.posZ,
                    entity.posX, entity.posY, entity.posZ
            ).grow(2D);

            for (PhysicsObject nearbyShip : physObjectWorld.getPhysObjectsInAABB(searchBox)) {
                if (nearbyShip.getShipData().isPeggedTo(entity)) continue;
                Vec3d possibleLocalMountPos = nearbyShip.transformVector(rawEntityPos, TransformType.GLOBAL_TO_SUBSPACE);
                BlockPos possibleLocalBlock = new BlockPos(possibleLocalMountPos);
                Optional<PhysicsObject> managingShip = ValkyrienUtils.getPhysoManagingBlock(entity.world, possibleLocalBlock);

                if (managingShip.isPresent() && managingShip.get() == nearbyShip) {
                    this.setAnchorMountData(possibleLocalMountPos, possibleLocalBlock);
                    return true;
                }
            }
        }
        catch (CalledFromWrongThreadException ignored) {
            return false;
        }

        return false;
    }
}
