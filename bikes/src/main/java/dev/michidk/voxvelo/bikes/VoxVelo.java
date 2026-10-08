package dev.michidk.voxvelo.bikes;

import dev.michidk.voxvelo.bikes.bike.BikeServerConfig;
import dev.michidk.voxvelo.bikes.ext.VoxVeloExtension;
import dev.michidk.voxvelo.bikes.network.BikeNetworking;
import dev.michidk.voxvelo.bikes.registry.ModComponents;
import dev.michidk.voxvelo.bikes.registry.ModEntities;
import dev.michidk.voxvelo.bikes.registry.ModItems;
import dev.michidk.voxvelo.bikes.registry.ModRecipes;
import dev.michidk.voxvelo.bikes.registry.ModSounds;
import dev.michidk.voxvelo.bikes.registry.ModStats;
import dev.michidk.voxvelo.bikes.registry.ModTickets;
import dev.michidk.voxvelo.bikes.track.TrackCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common (client and server) entrypoint. Never touches client hardware. */
public class VoxVelo implements ModInitializer {
	public static final String MOD_ID = "voxvelo_bikes";
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
