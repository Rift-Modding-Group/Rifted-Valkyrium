package org.valkyrienskies.mixin.mod.fairylights;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.block_relocation.IRelocationAwareTile;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

import javax.annotation.Nullable;

//a mixin that fixes hanging lights from fairy lights not being preserved when
//assembling and disassembling a ship
@Pseudo
@Mixin(targets = "com.pau101.fairylights.server.block.entity.BlockEntityFastener", remap = false)
@Implements(@Interface(iface = IRelocationAwareTile.class, prefix = "fairyLights$"))
public abstract class MixinBlockEntityFastener {
    @Nullable
    public TileEntity fairyLights$createRelocatedTile(BlockPos newPosition, @Nullable ShipData copiedBy) {
        //get translation shared by the fastener and any connected endpoint
        //that moves with it. for disassembly, also get the source ship so
        //endpoints can be classified by their current ship ownership.
        TileEntity fastenerTileEntity = (TileEntity) (Object) this;
        World world = fastenerTileEntity.getWorld();
        BlockPos oldPosition = fastenerTileEntity.getPos();
        BlockPos offset = newPosition.subtract(oldPosition);
        PhysicsObject sourceShip = null;
        if (copiedBy == null) sourceShip = ValkyrienUtils.getPhysoManagingBlock(world, oldPosition).orElse(null);

        //get nbt
        NBTTagCompound tileData = fastenerTileEntity.writeToNBT(new NBTTagCompound());
        NBTTagCompound capabilities = tileData.getCompoundTag("ForgeCaps");
        NBTTagList connections = capabilities.getCompoundTag("fairylights:fastener").getTagList("connections", 10);

        //iterate over connection nbt
        for (int index = 0; index < connections.tagCount(); index++) {
            NBTTagCompound connectionEntry = connections.getCompoundTagAt(index);
            NBTTagCompound destination = connectionEntry.getCompoundTag("connection").getCompoundTag("destination");

            //skip non-block stuff
            if (!destination.getString("type").equals("block")) continue;

            //get where the opposite endpoint would land if it follows this fastener through the same relocation
            BlockPos oldDestinationPosition = NBTUtil.getPosFromTag(destination.getCompoundTag("data"));
            BlockPos newDestinationPosition = oldDestinationPosition.add(offset);

            boolean destinationRelocates;
            //disassembly treats an endpoint as moving when the same ship currently manages both ends of the connection
            if (copiedBy == null) {
                PhysicsObject destinationShip = ValkyrienUtils.getPhysoManagingBlock(world, oldDestinationPosition).orElse(null);
                destinationRelocates = sourceShip != null && sourceShip == destinationShip;
            }
            //assembly uses the complete set of blocks copied into the new ship
            else destinationRelocates = copiedBy.getBlockPositions().contains(newDestinationPosition);

            //when both endpoints move, translating this fastener's stored
            //destination is enough because the other fastener relocates as well
            if (destinationRelocates) {
                destination.setTag("data", NBTUtil.createPosTag(newDestinationPosition));
                continue;
            }

            //when the opposite endpoint stays in place, update its tile data so
            //its reverse connection points to this fastener's new position
            TileEntity newTileEntity = world.getTileEntity(oldDestinationPosition);
            if (newTileEntity == null) continue;

            NBTTagCompound newTileData = newTileEntity.writeToNBT(new NBTTagCompound());
            NBTTagCompound newCapabilities = newTileData.getCompoundTag("ForgeCaps");
            if (!newCapabilities.hasKey("fairylights:fastener", 10)) continue;

            NBTTagList destinationConnections = newCapabilities.getCompoundTag("fairylights:fastener").getTagList("connections", 10);
            for (int destinationIndex = 0; destinationIndex < destinationConnections.tagCount(); destinationIndex++) {
                NBTTagCompound destinationEntry = destinationConnections.getCompoundTagAt(destinationIndex);

                if (!destinationEntry.getCompoundTag("uuid").equals(connectionEntry.getCompoundTag("uuid"))) {
                    continue;
                }

                NBTTagCompound reverseDestination = destinationEntry.getCompoundTag("connection").getCompoundTag("destination");
                if (!reverseDestination.getString("type").equals("block")) continue;

                BlockPos reversePosition = NBTUtil.getPosFromTag(reverseDestination.getCompoundTag("data"));
                if (!reversePosition.equals(oldPosition)) continue;

                //set the corrected reverse endpoint and send to client
                reverseDestination.setTag("data", NBTUtil.createPosTag(newPosition));
                newTileEntity.readFromNBT(newTileData);
                newTileEntity.markDirty();
                IBlockState state = world.getBlockState(oldDestinationPosition);
                world.notifyBlockUpdate(oldDestinationPosition, state, state, 2);
                break;
            }
        }

        //set relocated coordinates
        tileData.setInteger("x", newPosition.getX());
        tileData.setInteger("y", newPosition.getY());
        tileData.setInteger("z", newPosition.getZ());
        return TileEntity.create(world, tileData);
    }
}
