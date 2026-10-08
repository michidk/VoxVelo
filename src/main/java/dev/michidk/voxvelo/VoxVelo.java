package dev.michidk.voxvelo;

import dev.michidk.voxvelo.bike.BikeServerConfig;
import dev.michidk.voxvelo.ext.VoxVeloExtension;
import dev.michidk.voxvelo.network.BikeNetworking;
import dev.michidk.voxvelo.registry.ModComponents;
import dev.michidk.voxvelo.registry.ModEntities;
import dev.michidk.voxvelo.registry.ModItems;
import dev.michidk.voxvelo.registry.ModRecipes;
import dev.michidk.voxvelo.registry.ModSounds;
import dev.michidk.voxvelo.registry.ModStats;
import dev.michidk.voxvelo.registry.ModTickets;
import dev.michidk.voxvelo.track.TrackCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common (client and server) entrypoint. Never touches client hardware. */
public class VoxVelo implements ModInitializer {
	public static final String MOD_ID = "voxvelo";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModComponents.register();
		ModEntities.register();
		ModItems.register();
		ModSounds.register();
		ModStats.register();
		ModTickets.register();
		BikeServerConfig.load();
		ModRecipes.register();
		BikeNetworking.register();
		TrackCommand.register();
		// Add-ons such as the fitness integration plug in here; the raw bike mod has none.
		for (VoxVeloExtension extension : FabricLoader.getInstance().getEntrypoints(VoxVeloExtension.ENTRYPOINT, VoxVeloExtension.class)) {
			extension.onInitialize();
		}
	}
}
