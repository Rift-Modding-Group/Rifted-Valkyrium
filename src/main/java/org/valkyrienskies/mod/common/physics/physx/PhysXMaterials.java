package org.valkyrienskies.mod.common.physics.physx;

import org.jetbrains.annotations.NotNull;
import physx.physics.PxMaterial;
import physx.physics.PxPhysics;

/**
 * Common physX materials shared amongst all colliding objects
 * */
public enum PhysXMaterials {
    SHIP(0.55f, 0.55f, 0.05f),
    WORLD(0.8f, 0.8f, 0.02f),
    LIQUID(0.8f, 0.8f, 0.02f);

    private final float staticFriction;
    private final float dynamicFriction;
    private final float restitution;

    PhysXMaterials(float staticFriction, float dynamicFriction, float restitution) {
        this.staticFriction = staticFriction;
        this.dynamicFriction = dynamicFriction;
        this.restitution = restitution;
    }

    @NotNull
    public PxMaterial create(@NotNull PxPhysics physics) {
        return physics.createMaterial(this.staticFriction, this.dynamicFriction, this.restitution);
    }
}
