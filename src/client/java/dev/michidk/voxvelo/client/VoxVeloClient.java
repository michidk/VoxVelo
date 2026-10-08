package dev.michidk.voxvelo.client;

import dev.michidk.voxvelo.bike.BikeEntity;
import dev.michidk.voxvelo.client.ext.VoxVeloClientExtension;
import dev.michidk.voxvelo.client.input.VoxVeloKeys;
import dev.michidk.voxvelo.client.network.BikeControlSender;
import dev.michidk.voxvelo.client.render.BikeGeoRenderer;
import dev.michidk.voxvelo.client.render.BikeHelmetRenderer;
import dev.michidk.voxvelo.client.ui.ConfigScreen;
import dev.michidk.voxvelo.client.ui.MenuButton;
import dev.michidk.voxvelo.registry.ModEntities;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;

/** Client-only entrypoint: keyboard riding, rendering and configuration, plus any add-ons. */
public class VoxVeloClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		VoxVeloKeys.register();
		EntityRenderers.register(ModEntities.BICYCLE, BikeGeoRenderer::new);
		EntityRenderers.register(ModEntities.BICYCLE_PART, NoopRenderer::new);
		BikeHelmetRenderer.register();

		ClientContext ctx = ClientContext.init();
		BikeControlSender sender = new BikeControlSender(ctx.inputManager, ctx.config, ctx.mountListeners());
		// The rider's own client simulates the bike it rides, like a horse; the sender gives it the controls.
		BikeEntity.setDriver(sender);
		ctx.attachSender(sender);

		// Add-ons such as the fitness integration plug in here; the raw bike mod has none.
		List<VoxVeloClientExtension> extensions = FabricLoader.getInstance()
			.getEntrypoints(VoxVeloClientExtension.ENTRYPOINT, VoxVeloClientExtension.class);
		for (VoxVeloClientExtension extension : extensions) {
			extension.init(ctx);
		}

		// A way in for players who do not know the settings key: a button on the pause menu and the options menu.
		MenuButton.register();

		ClientPlayConnectionEvents.JOIN.register((handler, packetSender, minecraft) -> {
			ctx.sendRiderSettings();
			for (VoxVeloClientExtension extension : extensions) {
				extension.onJoin();
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			for (VoxVeloClientExtension extension : extensions) {
				extension.tick(client);
			}
			sender.tick(client);
			openConfigOnKey(client);
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			ctx.config.save();
			for (VoxVeloClientExtension extension : extensions) {
				extension.onStopping();
			}
		});
	}

	private static void openConfigOnKey(Minecraft client) {
		boolean pressed = false;
		while (VoxVeloKeys.OPEN_CONFIG.consumeClick()) {
			pressed = true;
		}
		if (pressed && client.gui.screen() == null) {
			client.gui.setScreen(new ConfigScreen(null));
		}
	}
}
