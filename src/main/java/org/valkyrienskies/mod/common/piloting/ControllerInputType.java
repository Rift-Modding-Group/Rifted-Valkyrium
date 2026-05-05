package org.valkyrienskies.mod.common.piloting;

/**
 * TODO: remove this enum and replace it with something much more independent
 * */
@Deprecated
public enum ControllerInputType {

    CaptainsChair(true), ShipHelm(true), Zepplin(false), Telegraph(true), LiftLever(true);

    private final boolean lockPlayerMovement;

    ControllerInputType(boolean lockPlayerMovement) {
        this.lockPlayerMovement = lockPlayerMovement;
    }

    public boolean shouldLockPlayerMovement() {
        return lockPlayerMovement;
    }

}
