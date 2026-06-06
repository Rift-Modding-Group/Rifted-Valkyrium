package org.valkyrienskies.mod.common.physics;

import org.valkyrienskies.mod.common.config.VSConfig;
import physx.PxTopLevelFunctions;
import physx.common.PxDefaultAllocator;
import physx.common.PxDefaultCpuDispatcher;
import physx.common.PxDefaultErrorCallback;
import physx.common.PxFoundation;
import physx.common.PxTolerancesScale;
import physx.physics.PxPhysics;

/**
 * Process-wide PhysX runtime objects. PhysX Foundation and Physics are global SDK objects; scenes
 * are per dimension and live in {@link PhysXWorldBackend}.
 */
public class PhysXRuntime {
    private static final Object LOCK = new Object();

    private static PxFoundation sharedFoundation;
    private static PxTolerancesScale sharedTolerances;
    private static PxPhysics sharedPhysics;
    private static PxDefaultCpuDispatcher sharedCpuDispatcher;
    private static int references;

    public final PxFoundation foundation;
    public final PxTolerancesScale tolerances;
    public final PxPhysics physics;
    public final PxDefaultCpuDispatcher cpuDispatcher;

    private PhysXRuntime(PxFoundation foundation, PxTolerancesScale tolerances, PxPhysics physics, PxDefaultCpuDispatcher cpuDispatcher) {
        this.foundation = foundation;
        this.tolerances = tolerances;
        this.physics = physics;
        this.cpuDispatcher = cpuDispatcher;
    }

    public static PhysXRuntime acquire() {
        synchronized (LOCK) {
            if (sharedFoundation == null || sharedPhysics == null || sharedCpuDispatcher == null) {
                initializeLocked();
            }
            references++;
            return new PhysXRuntime(sharedFoundation, sharedTolerances, sharedPhysics, sharedCpuDispatcher);
        }
    }

    public void release() {
        synchronized (LOCK) {
            if (references <= 0) return;
            references--;
        }
    }

    private static void initializeLocked() {
        PxDefaultAllocator allocator = new PxDefaultAllocator();
        PxDefaultErrorCallback errorCallback = new PxDefaultErrorCallback();
        sharedFoundation = PxTopLevelFunctions.CreateFoundation(
            PxTopLevelFunctions.getPHYSICS_VERSION(),
                allocator,
                errorCallback
        );
        if (sharedFoundation == null || sharedFoundation.getAddress() == 0L) {
            throw new IllegalStateException("PhysX CreateFoundation returned null. Check native PhysX initialization and previous shutdown errors.");
        }

        sharedTolerances = new PxTolerancesScale();
        sharedPhysics = PxTopLevelFunctions.CreatePhysics(
            PxTopLevelFunctions.getPHYSICS_VERSION(),
            sharedFoundation,
            sharedTolerances
        );
        if (sharedPhysics == null || sharedPhysics.getAddress() == 0L) {
            throw new IllegalStateException("PhysX CreatePhysics returned null. Check native PhysX initialization and SDK version compatibility.");
        }

        //boolean extensionsInitialized = PxTopLevelFunctions.InitExtensions(sharedPhysics);
        sharedCpuDispatcher = PxTopLevelFunctions.DefaultCpuDispatcherCreate(VSConfig.threadCount);
        if (sharedCpuDispatcher == null || sharedCpuDispatcher.getAddress() == 0L) {
            throw new IllegalStateException("PhysX DefaultCpuDispatcherCreate returned null.");
        }
    }
}
