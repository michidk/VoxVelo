package dev.michidk.voxelvelo.client;

import dev.michidk.voxelvelo.VoxelVelo;
import dev.michidk.voxelvelo.client.ext.VoxelVeloClientExtension;
import dev.michidk.voxelvelo.client.hud.BikeHud;
import dev.michidk.voxelvelo.client.input.VoxelVeloKeys;
import dev.michidk.voxelvelo.client.network.BikeControlSender;
import dev.michidk.voxelvelo.client.render.BikeGeoRenderer;
import dev.michidk.voxelvelo.client.render.BikeHelmetRenderer;
import dev.michidk.voxelvelo.client.ui.ConfigScreen;
import dev.michidk.voxelvelo.client.ui.MenuButton;
import dev.michidk.voxelvelo.registry.ModEntities;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;

/** Client-only entrypoint: keyboard riding, rendering, the riding HUD and configuration, plus any add-ons. */
public class VoxelVeloClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		VoxelVeloKeys.register();
		EntityRenderers.register(ModEntities.BICYCLE, BikeGeoRenderer::new);
		BikeHelmetRenderer.register();

		ClientContext ctx = ClientContext.init();
		BikeControlSender sender = new BikeControlSender(ctx.inputManager, ctx.mountListeners());
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, VoxelVelo.id("bike_hud"), new BikeHud(ctx, sender));

		// Add-ons such as the fitness integration plug in here; the raw bike mod has none.
		List<VoxelVeloClientExtension> extensions = FabricLoader.getInstance()
			.getEntrypoints(VoxelVeloClientExtension.ENTRYPOINT, VoxelVeloClientExtension.class);
		for (VoxelVeloClientExtension extension : extensions) {
			extension.init(ctx);
		}

		// A way in for players who do not know the settings key: a button on the pause menu and the options menu.
		MenuButton.register();

		ClientPlayConnectionEvents.JOIN.register((handler, packetSender, minecraft) -> {
			ctx.sendRiderSettings();
			for (VoxelVeloClientExtension extension : extensions) {
				extension.onJoin();
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			for (VoxelVeloClientExtension extension : extensions) {
				extension.tick(client);
			}
			sender.tick(client);
			openConfigOnKey(client);
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			ctx.config.save();
			for (VoxelVeloClientExtension extension : extensions) {
				extension.onStopping();
			}
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
