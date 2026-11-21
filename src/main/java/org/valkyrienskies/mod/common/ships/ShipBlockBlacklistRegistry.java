package org.valkyrienskies.mod.common.ships;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import valkyrienwarfare.api.ShipBlockBlacklist;

import javax.annotation.ParametersAreNonnullByDefault;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores server placement policies under the stable IDs persisted with ships.
 */
@ApiStatus.Internal
@ParametersAreNonnullByDefault
public class ShipBlockBlacklistRegistry {
    private static final Map<String, ShipBlockBlacklist> blacklists = new ConcurrentHashMap<>();

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
        ShipBlockBlacklist registered = blacklists.putIfAbsent(blacklistId, blacklist);
        if (registered != null && registered != blacklist) {
            throw new IllegalArgumentException("Reuse the existing ship blacklist instance for " + blacklistId);
        }
        return blacklistId;
    }

    @Nullable
    public static ShipBlockBlacklist get(String id) {
        return blacklists.get(id);
    }
}
