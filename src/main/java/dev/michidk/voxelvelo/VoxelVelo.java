package dev.michidk.voxelvelo;

import dev.michidk.voxelvelo.network.BikeNetworking;
import dev.michidk.voxelvelo.registry.ModEntities;
import dev.michidk.voxelvelo.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

/** Common (client and server) entrypoint. Never touches Bluetooth, sockets or other client hardware. */
public class VoxelVelo implements ModInitializer {
	public static final String MOD_ID = "voxelvelo";
	public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModEntities.register();
		ModItems.register();
		BikeNetworking.register();
	}
}
