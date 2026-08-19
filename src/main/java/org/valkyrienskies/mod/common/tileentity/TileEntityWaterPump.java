package org.valkyrienskies.mod.common.tileentity;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;

import java.util.List;

public class TileEntityWaterPump extends TileEntity implements ITickable {
    public static final int MIN_PUMP_RADIUS = 1;
    public static final int MAX_PUMP_RADIUS = 3;
    public static final int DEFAULT_PUMP_RADIUS = 2;
    public static final int RANGE_VISUALIZATION_DURATION_TICKS = 60;

    private int pumpRadius;
    private int rangeVisualizationTicks;

    public TileEntityWaterPump() {
        this.pumpRadius = DEFAULT_PUMP_RADIUS;
        this.rangeVisualizationTicks = 0;
    }

    @Override
    public void update() {
        if (this.rangeVisualizationTicks > 0) this.rangeVisualizationTicks--;

        AxisAlignedBB pumpRangeBB = this.getPumpRangeBB();
        List<Entity> entitiesInPumpRadius = this.world.getEntitiesWithinAABBExcludingEntity(null, pumpRangeBB);

        for (Entity entity : entitiesInPumpRadius) {
            IEntityShipDraggable draggable = entity.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
            if (draggable == null) continue;

            draggable.setTicksAirPocket(2);
            entity.setAir(300);
        }
    }

    //---pump radius management---
    public int cyclePumpRadius() {
        int nextRadius = this.pumpRadius == MAX_PUMP_RADIUS ? MIN_PUMP_RADIUS : this.pumpRadius + 1;
        this.pumpRadius = clampPumpRadius(nextRadius);
        this.markDirty();
        this.restartRangeVisualization();
        return this.pumpRadius;
    }

    @NotNull
    public AxisAlignedBB getPumpRangeBB() {
        double centerX = this.pos.getX() + 0.5;
        double centerY = this.pos.getY() + 0.5;
        double centerZ = this.pos.getZ() + 0.5;
        return new AxisAlignedBB(centerX, centerY, centerZ, centerX, centerY, centerZ).grow(this.pumpRadius + 0.5D);
    }

    //---pump range visualization---
    public int getRangeVisualizationTicks() {
        return this.rangeVisualizationTicks;
    }

    public void restartRangeVisualization() {
        this.rangeVisualizationTicks = RANGE_VISUALIZATION_DURATION_TICKS;
        if (this.world != null && !this.world.isRemote) {
            IBlockState blockState = this.world.getBlockState(this.pos);
            this.world.notifyBlockUpdate(this.pos, blockState, blockState, 2);
        }
    }

    //---nbt management---
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        compound.setInteger("PumpRadius", this.pumpRadius);
        return super.writeToNBT(compound);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        this.pumpRadius = compound.hasKey("PumpRadius") ? clampPumpRadius(compound.getInteger("PumpRadius")) : DEFAULT_PUMP_RADIUS;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return this.writeToNBT(super.getUpdateTag());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        NBTTagCompound updateTag = this.getUpdateTag();
        updateTag.setInteger("RangeVisualizationTicks", this.rangeVisualizationTicks);
        return new SPacketUpdateTileEntity(this.pos, 0, updateTag);
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        this.readFromNBT(pkt.getNbtCompound());
        int rangeVisualizationTicks = pkt.getNbtCompound().getInteger("RangeVisualizationTicks");
        this.rangeVisualizationTicks = Math.clamp(rangeVisualizationTicks, 0, RANGE_VISUALIZATION_DURATION_TICKS);
    }

    private static int clampPumpRadius(int pumpRadius) {
        return Math.clamp(pumpRadius, MIN_PUMP_RADIUS, MAX_PUMP_RADIUS);
    }
}
