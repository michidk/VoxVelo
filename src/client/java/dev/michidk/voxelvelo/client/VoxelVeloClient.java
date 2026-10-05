package dev.michidk.voxelvelo.client;

import dev.michidk.voxelvelo.bike.BikeType;
import dev.michidk.voxelvelo.client.input.BikeInputManager;
import dev.michidk.voxelvelo.client.input.VoxelVeloKeys;
import dev.michidk.voxelvelo.client.network.BikeControlSender;
import dev.michidk.voxelvelo.client.render.BikeModel;
import dev.michidk.voxelvelo.client.render.BikeRenderer;
import dev.michidk.voxelvelo.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.renderer.entity.EntityRenderers;

/** Client-only entrypoint: input devices, rendering and (later) HUD and hardware. */
public class VoxelVeloClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		VoxelVeloKeys.register();

		for (BikeType type : BikeType.values()) {
			ModelLayerRegistry.registerModelLayer(BikeModel.layer(type), () -> BikeModel.createLayer(type));
		}
		EntityRenderers.register(ModEntities.BICYCLE, BikeRenderer::new);

		BikeControlSender sender = new BikeControlSender(new BikeInputManager());
		ClientTickEvents.END_CLIENT_TICK.register(sender::tick);
	}
}
