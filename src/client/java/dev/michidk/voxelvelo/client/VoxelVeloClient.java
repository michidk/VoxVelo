package dev.michidk.voxelvelo.client;

import dev.michidk.voxelvelo.client.input.VoxelVeloKeys;
import dev.michidk.voxelvelo.client.network.BikeControlSender;
import dev.michidk.voxelvelo.client.render.BikeGeoRenderer;
import dev.michidk.voxelvelo.client.ui.ConfigScreen;
import dev.michidk.voxelvelo.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;

/** Client-only entrypoint: input devices, Bluetooth, rendering and configuration. */
public class VoxelVeloClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		VoxelVeloKeys.register();
		EntityRenderers.register(ModEntities.BICYCLE, BikeGeoRenderer::new);

		ClientContext ctx = ClientContext.init();
		BikeControlSender sender = new BikeControlSender(ctx.inputManager);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ctx.bluetooth.tick();
			if (client.level != null) {
				ctx.ftms.tick(ctx.config);
				ctx.obc.tick(ctx.config);
			}
			sender.tick(client);
			openConfigOnKey(client);
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			ctx.config.save();
			ctx.obc.shutdown();
			ctx.bluetooth.shutdown();
		});
	}

	private static void openConfigOnKey(Minecraft client) {
		boolean pressed = false;
		while (VoxelVeloKeys.OPEN_CONFIG.consumeClick()) {
			pressed = true;
		}
		if (pressed && client.gui.screen() == null) {
			client.gui.setScreen(new ConfigScreen(null));
		}
	}
}
