package org.valkyrienskies.addon.control.block.multiblocks;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.addon.control.MultiblockRegistry;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;

import java.util.List;

public class TileEntityValkyriumCompressorPart extends TileEntityMultiblockPartForce<ValkyriumCompressorMultiblockSchematic, TileEntityValkyriumCompressorPart> {
    private static final Vector3dc FORCE_NORMAL = new Vector3d(0, 1, 0);
    private double prevKeyframe;
    private double currentKeyframe;

    public TileEntityValkyriumCompressorPart() {
        super();
    }

    public TileEntityValkyriumCompressorPart(double maxThrust) {
        this();
        this.setMaxThrust(maxThrust);
        this.prevKeyframe = 0;
        this.currentKeyframe = 0;
    }

    @Override
    public void update() {
        super.update();
        this.prevKeyframe = this.currentKeyframe;
        this.currentKeyframe += 1.2;
        this.currentKeyframe = this.currentKeyframe % 99;
    }

    @Override
    public Vector3dc getForceOutputNormal(PhysicsObject object) {
        return FORCE_NORMAL;
    }

    @Override
    public void setThrustMultiplierGoal(double thrustMultiplierGoal) {
        // TODO: Something is fundamentally wrong here.
        if (this.isMaster() || this.getMaster() == this) {
            super.setThrustMultiplierGoal(thrustMultiplierGoal);
        }
        else this.getMaster().setThrustMultiplierGoal(thrustMultiplierGoal);
    }

    @Override
    public double getThrustMagnitude(PhysicsObject physicsObject) {
        if (this.isPartOfAssembledMultiblock() && this.getMaster() != null) {
            return this.getMaxThrust() * this.getMaster().getThrustMultiplierGoal();
        }
        return 0;
    }

    public double getCurrentKeyframe(double partialTick) {
        double increment = this.currentKeyframe - this.prevKeyframe;
        if (increment < 0) increment = (increment % 99) + 99;
        return this.prevKeyframe + (increment * partialTick) + 1;
    }

    @Override
    public boolean attemptToAssembleMultiblock(World worldIn, BlockPos pos, EnumFacing facing) {
        List<IMultiblockSchematic> valkyriumEngineMultiblockSchematics = MultiblockRegistry.getSchematicsWithPrefix(
                "multiblock_valkyrium_compressor"
        );
        for (IMultiblockSchematic schematic : valkyriumEngineMultiblockSchematics) {
            if (schematic.attemptToCreateMultiblock(worldIn, pos)) return true;
        }
        return false;
    }
}
