package org.valkyrienskies.mod.common.entity;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import org.valkyrienskies.mod.common.annotation.LegacyContent;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.valkyrienskies.mod.common.ships.ship_transform.CoordinateSpaceType;
import javax.annotation.Nonnull;

/**
 * For vanilla passenger's chair
 * */
@LegacyContent
public class EntityMountableChair extends EntityMountable {
    @LegacyContent
    public static void registerEntity(@Nonnull RegistryEvent.Register<EntityEntry> event, int entityId, int trackingRange) {
        ResourceLocation id = new ResourceLocation(ValkyrienSkiesMod.MOD_ID, "entity_mountable_chair");
        event.getRegistry().register(EntityEntryBuilder.create()
                .entity(EntityMountableChair.class)
                .id(id, entityId)
                .name(id.getPath())
                .tracker(trackingRange, 1, false)
                .build());
    }

    // Minecraft requires this constructor.
    @SuppressWarnings("unused")
    public EntityMountableChair(World worldIn) {
        super(worldIn);
    }

    public EntityMountableChair(World world, Vec3d mountPos, CoordinateSpaceType coordinateSpaceType, BlockPos chairPos) {
        super(world, mountPos, coordinateSpaceType, chairPos);
    }

    @Override
    public void onUpdate() {
        // Some error occurred, kill this chair.
        if (this.getReferencePosOptional().isEmpty()) {
            new IllegalStateException("Chair mountable entity has no reference position.").printStackTrace();
            this.setDead();
            return;
        }
        super.onUpdate();
    }
}
