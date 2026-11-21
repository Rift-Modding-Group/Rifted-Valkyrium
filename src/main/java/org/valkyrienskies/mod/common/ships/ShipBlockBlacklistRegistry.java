package org.valkyrienskies.mod.common.ships;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import valkyrienwarfare.api.ShipBlockBlacklist;

import javax.annotation.ParametersAreNonnullByDefault;
import java.lang.invoke.SerializedLambda;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores server placement policies under the stable IDs persisted with ships.
 */
@ApiStatus.Internal
@ParametersAreNonnullByDefault
public class ShipBlockBlacklistRegistry {
    private static final Map<String, RegisteredBlacklist> blacklists = new ConcurrentHashMap<>();

    private ShipBlockBlacklistRegistry() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Registers shared callbacks using their class or lambda implementation identity.
     */
    @NotNull
    public static synchronized String register(ShipBlockBlacklist blacklist) {
        Objects.requireNonNull(blacklist, "blacklist");
        Class<?> blacklistClass = blacklist.getClass();
        String blacklistId = blacklistClass.getName();
        if (blacklistClass.isHidden()) {
            try {
                Method writeReplace = blacklistClass.getDeclaredMethod("writeReplace");
                writeReplace.setAccessible(true);
                if (!(writeReplace.invoke(blacklist) instanceof SerializedLambda lambda)) {
                    throw new IllegalArgumentException("Ship blacklist must provide a stable implementation identity");
                }
                blacklistId = lambda.getImplClass() + "#" + lambda.getImplMethodName()
                        + lambda.getImplMethodSignature() + ":" + lambda.getImplMethodKind();
            } catch (ReflectiveOperationException exception) {
                throw new IllegalArgumentException("Cannot identify ship blacklist implementation", exception);
            }
        }
        RegisteredBlacklist registered = blacklists.get(blacklistId);
        if (registered != null && !registered.restored() && registered.blacklist() != blacklist) {
            throw new IllegalArgumentException("Reuse the existing ship blacklist instance for " + blacklistId);
        }
        blacklists.put(blacklistId, new RegisteredBlacklist(blacklist, false));
        return blacklistId;
    }

    /**
     * Restores stateless implementations from saved IDs before caching them for placement checks.
     */
    @Nullable
    public static ShipBlockBlacklist get(String id) {
        RegisteredBlacklist registered = blacklists.get(id);
        if (registered != null) return registered.blacklist();

        ShipBlockBlacklist restored = null;
        try {
            int methodSeparator = id.indexOf('#');
            if (methodSeparator >= 0) {
                int descriptorStart = id.indexOf('(', methodSeparator);
                int kindSeparator = id.lastIndexOf(':');
                if (descriptorStart < 0 || kindSeparator < descriptorStart
                        || Integer.parseInt(id.substring(kindSeparator + 1)) != MethodHandleInfo.REF_invokeStatic
                ) {
                    throw new IllegalArgumentException("Unsupported ship blacklist implementation ID: " + id);
                }
                Class<?> implementation = Class.forName(id.substring(0, methodSeparator).replace('/', '.'));
                MethodType methodType = MethodType.fromMethodDescriptorString(
                        id.substring(descriptorStart, kindSeparator), implementation.getClassLoader());
                MethodType predicateType = MethodType.methodType(boolean.class, World.class, BlockPos.class, IBlockState.class);
                if (!methodType.equals(predicateType)) {
                    throw new IllegalArgumentException("Saved ship blacklist requires captured arguments: " + id);
                }
                MethodHandle predicate = MethodHandles.privateLookupIn(implementation, MethodHandles.lookup()).findStatic(
                        implementation, id.substring(methodSeparator + 1, descriptorStart), methodType);
                restored = MethodHandleProxies.asInterfaceInstance(ShipBlockBlacklist.class, predicate);
            }
            else {
                Class<? extends ShipBlockBlacklist> implementation = Class.forName(id).asSubclass(ShipBlockBlacklist.class);
                for (Class<?> current = implementation; current != null && current != Object.class; current = current.getSuperclass()) {
                    for (Field field : current.getDeclaredFields()) {
                        if (!Modifier.isStatic(field.getModifiers())) {
                            throw new IllegalArgumentException("Saved ship blacklist requires instance state: " + id);
                        }
                    }
                }
                Constructor<? extends ShipBlockBlacklist> constructor = implementation.getDeclaredConstructor();
                constructor.setAccessible(true);
                restored = constructor.newInstance();
            }
        }
        catch (ReflectiveOperationException | IllegalArgumentException | ClassCastException | LinkageError exception) {
            ValkyrienSkiesMod.LOGGER.error("Cannot restore ship block blacklist {}", id, exception);
        }
        RegisteredBlacklist existing = blacklists.putIfAbsent(id, new RegisteredBlacklist(restored, true));
        if (existing != null) return existing.blacklist();
        return restored;
    }

    private record RegisteredBlacklist(@Nullable ShipBlockBlacklist blacklist, boolean restored) {}
}
