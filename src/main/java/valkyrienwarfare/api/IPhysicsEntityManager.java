package valkyrienwarfare.api;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 *
 * Legacy interface that was used to get IPhysicsEntity objects. Deprecated but kept
 * to maintain compat with old ass mods that still use this interface (fairy lights for example).
 * @deprecated Use {@link RiftedValkyriumAPI#getShipManagingBlock(World, BlockPos)} from now on
 */
@Deprecated
public interface IPhysicsEntityManager {
	/**
	 * This gets replaced at runtime if RV is installed.
	 */
	IPhysicsEntityManager INSTANCE = new DummyPhysicsEntityManager();

	/**
	 * If the given BlockPos is in ship space then this returns the IPhysicsEntity
	 * pertaining to the pos. If the BlockPos is not in ship space then returns
	 * null.
	 * 
	 * @param world
	 * @param pos
	 * @return
	 */
	@Nullable
    IPhysicsEntity getPhysicsEntityFromShipSpace(World world, BlockPos pos);

	/**
	 * 
	 * @param world
	 * @param pos
	 * @return true if the pos and world have a ship associated to them, false if
	 *         otherwise.
	 */
	default boolean isBlockPosManagedByPhysicsEntity(World world, BlockPos pos) {
		return getPhysicsEntityFromShipSpace(world, pos) != null;
	}
}