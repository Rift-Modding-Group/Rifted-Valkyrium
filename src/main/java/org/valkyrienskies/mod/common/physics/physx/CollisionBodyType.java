package org.valkyrienskies.mod.common.physics.physx;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.mod.common.physics.physx.bodies.AbstractPhysXCollisionObject;
import org.valkyrienskies.mod.common.physics.physx.bodies.PhysXBlockSectionBody;
import org.valkyrienskies.mod.common.physics.physx.bodies.PhysXEntityBody;
import org.valkyrienskies.mod.common.physics.physx.bodies.PhysXShipBody;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public enum CollisionBodyType {
    SHIP(PhysXShipBody.class),
    BLOCK_SECTION(PhysXBlockSectionBody.class),
    ENTITY(PhysXEntityBody.class);

    @NotNull
    private final Class<? extends AbstractPhysXCollisionObject> bodyClass;

    CollisionBodyType(@NotNull Class<? extends AbstractPhysXCollisionObject> bodyClass) {
        this.bodyClass = bodyClass;
    }

    @NotNull
    public static Map<CollisionBodyType, Map<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject>> createRegistry() {
        HashMap<CollisionBodyType, Map<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject>> registry = new LinkedHashMap<>();
        for (CollisionBodyType bodyType : values()) {
            registry.put(bodyType, new HashMap<>());
        }
        return registry;
    }

    public void add(
            @NotNull Map<CollisionBodyType, Map<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject>> registry,
            @NotNull AbstractPhysXCollisionObject body
    ) {
        if (!this.bodyClass.isInstance(body)) {
            throw new IllegalArgumentException(body.getClass().getSimpleName() + " is not a " + this.name() + " collision body");
        }
        this.getBodies(registry).put(body.getIdentifier(), body);
    }

    @Nullable
    public AbstractPhysXCollisionObject get(
            @NotNull Map<CollisionBodyType, Map<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject>> registry,
            @NotNull AbstractPhysXCollisionObject.Identifier identifier
    ) {
        return this.getBodies(registry).get(identifier);
    }

    @NotNull
    public Map<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject> getBodies(
            @NotNull Map<CollisionBodyType, Map<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject>> registry
    ) {
        Map<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject> bodies = registry.get(this);
        if (bodies == null) {
            throw new IllegalStateException("Missing collision body registry for " + this);
        }
        return bodies;
    }
}
