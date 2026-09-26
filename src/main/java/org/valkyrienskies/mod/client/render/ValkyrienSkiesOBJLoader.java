package org.valkyrienskies.mod.client.render;

import java.io.FileNotFoundException;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.client.model.obj.OBJModel;
import org.jspecify.annotations.NonNull;
import org.valkyrienskies.addon.control.ValkyrienSkiesControl;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;

/**
 * Loads OBJ models from the Valkyrien Skies asset domains without relying on Forge's shared OBJ
 * loader registration state. For StellarCore compat
 */
public class ValkyrienSkiesOBJLoader implements ICustomModelLoader {
    private IResourceManager resourceManager;

    @Override
    public void onResourceManagerReload(@NonNull IResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    @Override
    public boolean accepts(ResourceLocation modelLocation) {
        String namespace = modelLocation.getNamespace();
        return (ValkyrienSkiesMod.MOD_ID.equals(namespace) || ValkyrienSkiesControl.MOD_ID.equals(namespace))
            && modelLocation.getPath().endsWith(".obj");
    }

    @Override
    public IModel loadModel(@NonNull ResourceLocation modelLocation) throws Exception {
        IResource resource;
        try {
            resource = this.resourceManager.getResource(modelLocation);
        }
        catch (FileNotFoundException exception) {
            String path = modelLocation.getPath();
            if (path.startsWith("models/block/")) {
                resource = this.resourceManager.getResource(new ResourceLocation(
                        modelLocation.getNamespace(),
                        "models/item/" + path.substring("models/block/".length())
                ));
            }
            else if (path.startsWith("models/item/")) {
                resource = this.resourceManager.getResource(new ResourceLocation(
                        modelLocation.getNamespace(),
                        "models/block/" + path.substring("models/item/".length())
                ));
            }
            else {
                throw exception;
            }
        }

        try (IResource closeableResource = resource) {
            OBJModel.Parser parser = new OBJModel.Parser(closeableResource, this.resourceManager);
            try {
                return parser.parse();
            }
            catch (Exception exception) {
                throw new ModelLoaderRegistry.LoaderException("Error loading model previously: " + modelLocation, exception);
            }
        }
    }
}
