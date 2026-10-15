package valkyrienwarfare.api;

import net.minecraft.tileentity.TileEntity;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Reports whether a component currently keeps a ship assembled.
 * <p>
 * A {@link TileEntity} or shared controller should register after finding its physical ship and
 * unregister when they are invalidated, unloaded, relocated, or moved to another ship. A provider
 * may remain registered while inactive; {@link #canMaintainShip()} is queried when the ship needs
 * to know whether any registered component is active.
 */
@FunctionalInterface
@ParametersAreNonnullByDefault
public interface IShipMaintenanceProvider {
    /**
     * @return {@code true} while this component can keep its registered ship assembled
     */
    boolean canMaintainShip();
}
