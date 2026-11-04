package valkyrienwarfare.api;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Legacy implementation of IPhysicsEntityManager thats was used when RV isn't
 * installed. It now redirects to {@link RiftedValkyriumAPI#getShipManagingBlock(World, BlockPos)}
 * (which should be used moving forward), and is kept to maintain compat with old
 * ass mods that still somehow rely on this interface.
 */
@Deprecated
class DummyPhysicsEntityManager implements IPhysicsEntityManager {
	@Override
	public IPhysicsEntity getPhysicsEntityFromShipSpace(World world, BlockPos pos) {
		return RiftedValkyriumAPI.getPhysoManagingBlock(world, pos).orElse(null);
	}
}
