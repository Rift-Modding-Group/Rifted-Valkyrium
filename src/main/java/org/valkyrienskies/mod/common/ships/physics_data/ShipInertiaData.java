package org.valkyrienskies.mod.common.ships.physics_data;

import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import javax.annotation.Nonnull;

/**
 * Stores the data of the ship mas and inertia matrix.
 */
public class ShipInertiaData {

    double gameTickMass = 0;
    @Nonnull
    Matrix3dc gameMoITensor = new Matrix3d();
    @Nonnull
    Vector3dc gameTickCenterOfMass = new Vector3d();

    public double getGameTickMass() {
        return gameTickMass;
    }

    public void setGameTickMass(double gameTickMass) {
        this.gameTickMass = gameTickMass;
    }

    @Nonnull
    public Matrix3dc getGameMoITensor() {
        return gameMoITensor;
    }

    public void setGameMoITensor(@Nonnull Matrix3dc gameMoITensor) {
        this.gameMoITensor = gameMoITensor;
    }

    @Nonnull
    public Vector3dc getGameTickCenterOfMass() {
        return gameTickCenterOfMass;
    }

    public void setGameTickCenterOfMass(@Nonnull Vector3dc gameTickCenterOfMass) {
        this.gameTickCenterOfMass = gameTickCenterOfMass;
    }
}
