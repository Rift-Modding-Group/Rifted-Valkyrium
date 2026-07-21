package org.valkyrienskies.mod.common.capability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.capability.anchored_mount.IShipAnchoredMount;
import org.valkyrienskies.mod.common.capability.anchored_mount.ImplCapabilityShipAnchoredMount;
import org.valkyrienskies.mod.common.capability.entity_backup.ICapabilityEntityBackup;
import org.valkyrienskies.mod.common.capability.entity_backup.ImplCapabilityEntityBackup;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.ImplCapabilityEntityShipDraggable;
import org.valkyrienskies.mod.common.capability.framework.VSDefaultCapabilityProvider;
import org.valkyrienskies.mod.common.capability.framework.VSDefaultCapabilityProviderTransient;
import org.valkyrienskies.mod.common.capability.framework.VSDefaultCapabilityStorage;
import org.valkyrienskies.mod.common.capability.framework.VSDefaultCapabilityTransientStorage;
import org.valkyrienskies.mod.common.capability.ship_pilot.IShipPilot;
import org.valkyrienskies.mod.common.capability.ship_pilot.ImplCapabilityShipPilot;
import org.valkyrienskies.mod.common.capability.ship_world.IShipWorld;
import org.valkyrienskies.mod.common.capability.ship_world.ImplCapabilityShipWorld;

import javax.annotation.Nonnull;

@EventBusSubscriber(modid = ValkyrienSkiesMod.MOD_ID)
public class VSCapabilityRegistry {
    @CapabilityInject(VSWorldDataCapability.class)
    public static final Capability<VSWorldDataCapability> VS_WORLD_DATA = getNull();

    @CapabilityInject(IShipWorld.class)
    public static final Capability<IShipWorld> VS_SHIP_WORLD = getNull();

    @CapabilityInject(ICapabilityEntityBackup.class)
    public static final Capability<ICapabilityEntityBackup> VS_ENTITY_BACKUP = getNull();

    @CapabilityInject(IShipPilot.class)
    public static final Capability<IShipPilot> VS_SHIP_PILOT = getNull();

    @CapabilityInject(IEntityShipDraggable.class)
    public static final Capability<IEntityShipDraggable> VS_ENTITY_SHIP_DRAGGABLE = getNull();

    @CapabilityInject(IShipAnchoredMount.class)
    public static final Capability<IShipAnchoredMount> VS_SHIP_ANCHORED_MOUNT = getNull();

    @SubscribeEvent
    public static void attachWorldCapabilities(AttachCapabilitiesEvent<World> event) {
        event.addCapability(
            new ResourceLocation(ValkyrienSkiesMod.MOD_ID, "world_data_capability"),
            new VSDefaultCapabilityProvider<>(VS_WORLD_DATA)
        );

        event.addCapability(
                new ResourceLocation(ValkyrienSkiesMod.MOD_ID, "world_ship_capability"),
                new VSDefaultCapabilityProviderTransient<>(VS_SHIP_WORLD)
        );
    }

    @SubscribeEvent
    public static void attachEntityCapabilities(AttachCapabilitiesEvent<Entity> event) {
        event.addCapability(
                new ResourceLocation(ValkyrienSkiesMod.MOD_ID, "entity_backup_capability"),
                new VSDefaultCapabilityProviderTransient<>(VS_ENTITY_BACKUP)
        );

        event.addCapability(
                new ResourceLocation(ValkyrienSkiesMod.MOD_ID, "entity_ship_draggable_capability"),
                new VSDefaultCapabilityProviderTransient<>(VS_ENTITY_SHIP_DRAGGABLE)
        );

        event.addCapability(
                new ResourceLocation(ValkyrienSkiesMod.MOD_ID, "ship_anchored_mount_capability"),
                new VSDefaultCapabilityProviderTransient<>(VS_SHIP_ANCHORED_MOUNT)
        );

        if (event.getObject() instanceof EntityPlayer) {
            event.addCapability(
                    new ResourceLocation(ValkyrienSkiesMod.MOD_ID, "ship_pilot"),
                    new VSDefaultCapabilityProviderTransient<>(VS_SHIP_PILOT)
            );
        }
    }

    public static void registerCapabilities() {
        CapabilityManager.INSTANCE.register(
                VSWorldDataCapability.class,
                new VSDefaultCapabilityStorage<>(),
                VSWorldDataCapability::new
        );

        CapabilityManager.INSTANCE.register(
                IShipWorld.class,
                new VSDefaultCapabilityTransientStorage<>(),
                ImplCapabilityShipWorld::new
        );

        CapabilityManager.INSTANCE.register(
                ICapabilityEntityBackup.class,
                new VSDefaultCapabilityTransientStorage<>(),
                ImplCapabilityEntityBackup::new
        );

        CapabilityManager.INSTANCE.register(
                IShipPilot.class,
                new VSDefaultCapabilityTransientStorage<>(),
                ImplCapabilityShipPilot::new
        );

        CapabilityManager.INSTANCE.register(
                IEntityShipDraggable.class,
                new VSDefaultCapabilityTransientStorage<>(),
                ImplCapabilityEntityShipDraggable::new
        );

        CapabilityManager.INSTANCE.register(
                IShipAnchoredMount.class,
                new VSDefaultCapabilityTransientStorage<>(),
                ImplCapabilityShipAnchoredMount::new
        );
    }


    /**
     * Used to trick the IDE into thinking that a capability is not null
     *
     * @return null
     * @see <a href="https://stackoverflow.com/questions/46512161/disable-constant-conditions-except
     * ions-inspection-for-field-in-intellij-idea">StackOverflow</a>
     */
    @Nonnull
    @SuppressWarnings({"ConstantConditions", "SameReturnValue"})
    public static <T> T getNull() {
        return null;
    }
}
