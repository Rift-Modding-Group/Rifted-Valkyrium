package org.valkyrienskies.mod.common.capability.framework;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.util.EnumFacing;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.capability.VSWorldDataCapability;
import org.valkyrienskies.mod.common.util.jackson.VSJacksonUtil;

/**
 * Implement as follows
 *
 * <pre>{@code
 * public class VSWorldDataCapability extends VSDefaultCapability<VSWorldData> {
 *     public VSWorldDataCapability(ObjectMapper mapper) {
 *         super(VSWorldData.class, VSWorldData::new, mapper);
 *     }
 *
 *     public VSWorldDataCapability() {
 *         super(VSWorldData.class, VSWorldData::new);
 *     }
 * }
 * }</pre>
 *
 * @param <K> The type of object this capability should store
 * @see VSWorldDataCapability
 */

@ParametersAreNonnullByDefault
public abstract class VSDefaultCapability<K> {
    private final ObjectMapper mapper;
    private final Class<K> kClass;
    @Nonnull
    private K instance;
    private Supplier<K> factory;

    public VSDefaultCapability(Class<K> kClass, Supplier<K> factory) {
        this(kClass, factory, VSJacksonUtil.getDefaultMapper());
    }

    public VSDefaultCapability(Class<K> kClass, Supplier<K> factory, ObjectMapper mapper) {
        this.kClass = kClass;
        this.factory = factory;
        this.instance = factory.get();
        ValkyrienSkiesMod.LOGGER.debug("CONSTRUCTED INSTANCE: " + instance);
        this.mapper = mapper;
    }

    @Nullable
    public NBTTagByteArray writeNBT(EnumFacing side) {
        long time = System.currentTimeMillis();
        byte[] value;
        try {
            value = getMapper().writeValueAsBytes(instance);
            ValkyrienSkiesMod.LOGGER.debug("VS serialization took {} ms. Writing data of size {} KB. ({})",
                System.currentTimeMillis() - time, value.length / Math.pow(2, 10),
                instance.getClass().getSimpleName());
        } catch (Exception ex) {
            ValkyrienSkiesMod.LOGGER.fatal("Something just broke horrifically. Be wary of your data. "
                + "This will crash the game in future releases", ex);
            value = new byte[0];
        }
        return new NBTTagByteArray(value);
    }

    public @NotNull K readNBT(NBTBase base, EnumFacing side) {
        long time = System.currentTimeMillis();

        try {
            byte[] value = ((NBTTagByteArray) base).getByteArray();
            this.instance = mapper.readValue(value, kClass);
            ValkyrienSkiesMod.LOGGER.info("VS deserialization took {} ms. Reading data of size {} KB.",
                System.currentTimeMillis() - time, value.length / Math.pow(2, 10));
        } catch (IOException | ClassCastException ex) {
            ValkyrienSkiesMod.LOGGER.fatal("Failed to read your ship data? Ships will probably be missing", ex);
            this.instance = factory.get();
        }

        // Possibly redundant null check. TODO: remove
        if (this.instance == null) {
            ValkyrienSkiesMod.LOGGER.fatal("Failed to read your ship data? Ships will probably be missing");
            this.instance = factory.get();
        }

        return this.instance;
    }

    public @NotNull K get() {
        return instance;
    }

    public void set(K instance) {
        this.instance = instance;
    }

    protected ObjectMapper getMapper() {
        return mapper;
    }

}
