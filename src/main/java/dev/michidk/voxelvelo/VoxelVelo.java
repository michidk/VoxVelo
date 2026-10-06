package dev.michidk.voxelvelo;

import dev.michidk.voxelvelo.ext.VoxelVeloExtension;
import dev.michidk.voxelvelo.network.BikeNetworking;
import dev.michidk.voxelvelo.registry.ModEntities;
import dev.michidk.voxelvelo.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

/** Common (client and server) entrypoint. Never touches client hardware. */
public class VoxelVelo implements ModInitializer {
	public static final String MOD_ID = "voxelvelo";
	public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		dev.michidk.voxelvelo.registry.ModComponents.register();
		ModEntities.register();
		ModItems.register();
        dev.michidk.voxelvelo.registry.ModMenus.register();
        dev.michidk.voxelvelo.bike.BikeServerConfig.load();
        dev.michidk.voxelvelo.recipe.BikeCraftingRecipe.register();
		BikeNetworking.register();
        dev.michidk.voxelvelo.track.TrackCommand.register();
		// Add-ons such as the fitness integration plug in here; the raw bike mod has none.
		for (VoxelVeloExtension extension : FabricLoader.getInstance().getEntrypoints(VoxelVeloExtension.ENTRYPOINT, VoxelVeloExtension.class)) {
			extension.onInitialize();
		}
	}
}
