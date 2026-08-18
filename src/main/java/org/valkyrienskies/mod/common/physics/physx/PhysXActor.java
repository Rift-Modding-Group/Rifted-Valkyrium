package org.valkyrienskies.mod.common.physics.physx;

import org.jetbrains.annotations.NotNull;
import physx.physics.*;

public enum PhysXActor {
    SHIP(0.55f, 0.55f, 0.05f, PxCombineModeEnum.eAVERAGE),
    SOLID(0.8f, 0.8f, 0.02f, PxCombineModeEnum.eAVERAGE),
    LIQUID(0.8f, 0.8f, 0.02f, PxCombineModeEnum.eAVERAGE);

    private final float staticFriction;
    private final float dynamicFriction;
    private final float restitution;
    @NotNull
    private final PxCombineModeEnum frictionCombineMode;

    PhysXActor(float staticFriction, float dynamicFriction, float restitution, @NotNull PxCombineModeEnum frictionCombineMode) {
        this.staticFriction = staticFriction;
        this.dynamicFriction = dynamicFriction;
        this.restitution = restitution;
        this.frictionCombineMode = frictionCombineMode;
    }

    @NotNull
    public PxMaterial createMaterial(@NotNull PxPhysics physics) {
        PxMaterial material = physics.createMaterial(this.staticFriction, this.dynamicFriction, this.restitution);
        material.setFrictionCombineMode(this.frictionCombineMode);
        return material;
    }

    public void setFilter(@NotNull PxShape shape) {
        PxFilterData filterData = new PxFilterData(this.bit(), this.mask(), 0, 0);
        shape.setSimulationFilterData(filterData);
        shape.setQueryFilterData(filterData);
        filterData.destroy();
    }

    private int bit() {
        return 1 << this.ordinal();
    }

    private int mask() {
        return switch (this) {
            case SHIP -> mask(SHIP, SOLID, LIQUID);
            case SOLID, LIQUID -> mask(SHIP);
        };
    }

    private static int mask(PhysXActor... actors) {
        int mask = 0;
        for (PhysXActor actor : actors) mask |= actor.bit();
        return mask;
    }
}
