package org.valkyrienskies.mod.common.tileentity;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.mod.common.ships.ship_transform.CoordinateSpaceType;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.entity.EntityMountableChair;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import org.valkyrienskies.api.TransformType;

// TODO: FIX THIS CLASS
public class TileEntityPassengerChair extends TileEntity /*implements IRelocationAwareTile*/ {
    // UUID of the mounting entity this chair is using to hold its passenger.
    @Nullable
    private UUID chairEntityUUID;

    public TileEntityPassengerChair() {
        this.chairEntityUUID = null;
    }

    public void tryToMountPlayerToChair(EntityPlayer player, Vec3d mountPos) {
        if (this.getWorld().isRemote) {
            throw new IllegalStateException("tryToMountPlayerToChair is not designed to be called on client side!");
        }
        boolean isChairEmpty;
        if (this.chairEntityUUID != null) {
            Entity chairEntity = ((WorldServer) this.getWorld()).getEntityFromUuid(chairEntityUUID);
            if (chairEntity != null) {
                if (chairEntity.isDead || chairEntity.isBeingRidden()) {
                    // Dead entity, chair is empty.
                    this.chairEntityUUID = null;
                    this.markDirty();
                    isChairEmpty = true;
                }
                else {
                    // Everything checks out, this chair is not empty.
                    isChairEmpty = false;
                }
            }
            else {
                // Either null or not a chair entity (somehow?). Just consider this chair as empty
                this.chairEntityUUID = null;
                this.markDirty();
                isChairEmpty = true;
            }
        }
        else {
            // No UUID for a chair entity, so this chair must be empty.
            isChairEmpty = true;
        }

        if (isChairEmpty) {
            // Chair is guaranteed empty.
            Optional<PhysicsObject> physicsObject = ValkyrienUtils.getPhysoManagingBlock(this.getWorld(), this.getPos());
            CoordinateSpaceType mountCoordType = physicsObject.isPresent() ?
                    CoordinateSpaceType.SUBSPACE_COORDINATES : CoordinateSpaceType.GLOBAL_COORDINATES;
            EntityMountableChair entityMountable = new EntityMountableChair(this.getWorld(), mountPos, mountCoordType, this.getPos());
            this.chairEntityUUID = entityMountable.getPersistentID();
            this.markDirty();
            this.getWorld().spawnEntity(entityMountable);
            player.startRiding(entityMountable);
        }
    }

    @Override
    public @NotNull NBTTagCompound writeToNBT(NBTTagCompound compound) {
        compound.setBoolean("has_chair_entity", this.chairEntityUUID != null);
        if (this.chairEntityUUID != null) {
            compound.setUniqueId("chair_entity_uuid", this.chairEntityUUID);
        }
        return super.writeToNBT(compound);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        if (compound.getBoolean("has_chair_entity")) {
            this.chairEntityUUID = compound.getUniqueId("chair_entity_uuid");
        }
        else {
            this.chairEntityUUID = null;
        }
        super.readFromNBT(compound);
    }

    public void onBlockBroken(IBlockState state) {
        if (this.chairEntityUUID != null) {
            // Kill the chair entity.
            Entity chairEntity = ((WorldServer) this.getWorld()).getEntityFromUuid(this.chairEntityUUID);
            if (chairEntity != null) chairEntity.setDead();
        }
    }

    public @NotNull TileEntity createRelocatedTile(BlockPos newPos, ShipTransform transform,
        CoordinateSpaceType coordinateSpaceType) {
        TileEntityPassengerChair relocatedTile = new TileEntityPassengerChair();
        relocatedTile.setWorld(this.getWorld());
        relocatedTile.setPos(newPos);

        if (this.chairEntityUUID != null) {
            EntityMountableChair chairEntity = (EntityMountableChair) ((WorldServer) this.getWorld()).getEntityFromUuid(this.chairEntityUUID);
            if (chairEntity != null) {
                Vec3d newMountPos = transform.transform(chairEntity.getMountPos(), TransformType.SUBSPACE_TO_GLOBAL);
                chairEntity.setMountValues(newMountPos, coordinateSpaceType, newPos);
            }
            else this.chairEntityUUID = null;
        }

        relocatedTile.chairEntityUUID = this.chairEntityUUID;
        // Move everything to the new tile.
        this.chairEntityUUID = null;
        this.markDirty();
        return relocatedTile;
    }
}
