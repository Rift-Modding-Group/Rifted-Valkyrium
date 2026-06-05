package org.valkyrienskies.mod.common.physics;

import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.mod.common.config.VSConfig;
import physx.PxTopLevelFunctions;
import physx.common.*;
import physx.physics.PxPhysics;
import physx.physics.PxScene;
import physx.physics.PxSceneDesc;

/**
 * To be created for every loaded dimension on the server.
 * This is a wrapper for PhysX, where physics objects exist in.
 * */
public class PhysXWorldBackend {
    @NotNull
    public final PxFoundation foundation;
    @NotNull
    public final PxTolerancesScale tolerances;
    @NotNull
    public final PxPhysics physics;
    @NotNull
    public final PxDefaultCpuDispatcher cpuDispatcher;
    @NotNull
    public final PxScene scene;

    public PhysXWorldBackend() {
        this.foundation = PxTopLevelFunctions.CreateFoundation(
                PxTopLevelFunctions.getPHYSICS_VERSION(),
                new PxDefaultAllocator(),
                new PxDefaultErrorCallback()
        );
        this.tolerances = new PxTolerancesScale();
        this.physics = PxTopLevelFunctions.CreatePhysics(
                PxTopLevelFunctions.getPHYSICS_VERSION(),
                this.foundation,
                this.tolerances
        );
        this.cpuDispatcher = PxTopLevelFunctions.DefaultCpuDispatcherCreate(VSConfig.threadCount);

        PxVec3 gravityVec = new PxVec3((float) VSConfig.gravityVecX, (float) VSConfig.gravityVecY, (float) VSConfig.gravityVecZ);
        PxSceneDesc sceneDesc = new PxSceneDesc(this.tolerances);
        sceneDesc.setGravity(gravityVec);
        sceneDesc.setCpuDispatcher(this.cpuDispatcher);
        sceneDesc.setFilterShader(PxTopLevelFunctions.DefaultFilterShader());
        this.scene = this.physics.createScene(sceneDesc);

        gravityVec.destroy();
        sceneDesc.destroy();
    }

    //to use to upload the physics stuff
    public void update(float tick) {
        if (tick <= 0) return;
        if (this.scene.simulate(tick)) this.scene.fetchResults(true);
    }

    //this is gonna be used to destroy all the physics stuff on unloading the world/dimension
    public void close() {
        this.foundation.release();
        this.tolerances.destroy();
        this.physics.release();
        //this.cpuDispatcher.destroy();
        this.scene.release();
    }
}
