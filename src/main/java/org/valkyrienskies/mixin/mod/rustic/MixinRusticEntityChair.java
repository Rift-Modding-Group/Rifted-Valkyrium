package org.valkyrienskies.mixin.mod.rustic;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.anchored_mount.IShipAnchoredMount;

/**
 * fuck rustic chairs
 * this is to make rustic chairs not autodismount the player when ship is going too fast and they try to sit
 * */
@Mixin(targets = "rustic.common.blocks.BlockChair$EntityChair", remap = false)
public abstract class MixinRusticEntityChair implements IEntityAdditionalSpawnData {
    @Override
    public void writeSpawnData(ByteBuf buffer) {
        Entity thisEntity = (Entity) (Object) this;
        IShipAnchoredMount anchoredMount = thisEntity.getCapability(VSCapabilityRegistry.VS_SHIP_ANCHORED_MOUNT, null);
        boolean anchoredToShip = anchoredMount != null && (anchoredMount.isAnchoredToShip() || anchoredMount.tryAnchorMount(thisEntity));
        buffer.writeBoolean(anchoredToShip);
        if (anchoredToShip) {
            Vec3d localMountPos = anchoredMount.getLocalMountPos();
            buffer.writeDouble(localMountPos.x);
            buffer.writeDouble(localMountPos.y);
            buffer.writeDouble(localMountPos.z);
            buffer.writeLong(anchoredMount.getLocalAnchorBlock().toLong());
        }
    }

    @Override
    public void readSpawnData(ByteBuf additionalData) {
        if (!additionalData.readBoolean()) return;

        Vec3d localMountPos = new Vec3d(additionalData.readDouble(), additionalData.readDouble(), additionalData.readDouble());
        BlockPos localAnchorBlock = BlockPos.fromLong(additionalData.readLong());
        Entity thisEntity = (Entity) (Object) this;
        IShipAnchoredMount anchoredMount = thisEntity.getCapability(VSCapabilityRegistry.VS_SHIP_ANCHORED_MOUNT, null);
        if (anchoredMount != null) anchoredMount.setAnchorMountData(localMountPos, localAnchorBlock);
    }
}
