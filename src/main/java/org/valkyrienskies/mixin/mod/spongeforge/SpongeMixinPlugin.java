package org.valkyrienskies.mixin.mod.spongeforge;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class SpongeMixinPlugin implements IMixinConfigPlugin {
    private static final String SPONGE_PROBE = "org/spongepowered/api/world/BlockChangeFlag.class";

    private boolean spongePresent;

    @Override
    public void onLoad(String mixinPackage) {
        ClassLoader classLoader = SpongeMixinPlugin.class.getClassLoader();
        this.spongePresent = classLoader.getResource(SPONGE_PROBE) != null;
    }

    @Override
    public String getRefMapperConfig() {
        return "";
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return this.spongePresent;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return List.of();
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}
