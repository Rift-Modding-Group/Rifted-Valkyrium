package org.valkyrienskies.mod.common.network;

/**
 * Turn this into a capability
 * */
public interface IHasPlayerMovementData {
    void setPlayerMovementData(PlayerMovementData playerMovementData);
    PlayerMovementData getPlayerMovementData();
}
