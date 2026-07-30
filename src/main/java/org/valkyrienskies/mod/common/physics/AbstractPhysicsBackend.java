package org.valkyrienskies.mod.common.physics;

import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;

import java.util.Collection;

/**
 * Make all physics backends for all physics engines this mod supports extend this class.
 * Contains common abstract methods and fields.
 * */
public abstract class AbstractPhysicsBackend {
    public abstract void update(@NotNull World hostWorld, @NotNull Collection<PhysicsObject> shipsWithPhysics, double timeStep);

    public abstract void close();
}
