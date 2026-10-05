package dev.michidk.voxelvelo.client;

import dev.michidk.voxelvelo.VoxelVelo;
import dev.michidk.voxelvelo.client.hud.BikeHud;
import dev.michidk.voxelvelo.client.hud.HeartRateHud;
import dev.michidk.voxelvelo.client.hud.JunctionPrompt;
import dev.michidk.voxelvelo.client.hud.StatsOverlay;
import dev.michidk.voxelvelo.network.RiderStatsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;
import dev.michidk.voxelvelo.client.input.VoxelVeloKeys;
import dev.michidk.voxelvelo.client.network.BikeControlSender;
import dev.michidk.voxelvelo.client.render.BikeGeoRenderer;
import dev.michidk.voxelvelo.client.ui.ConfigScreen;
import dev.michidk.voxelvelo.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;

/** Client-only entrypoint: input devices, Bluetooth, rendering and configuration. */
public class VoxelVeloClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		VoxelVeloKeys.register();
        net.minecraft.client.gui.screens.MenuScreens.register(dev.michidk.voxelvelo.registry.ModMenus.MAINTENANCE,
                dev.michidk.voxelvelo.client.ui.BikeMaintenanceScreen::new);
		EntityRenderers.register(ModEntities.BICYCLE, BikeGeoRenderer::new);

		ClientContext ctx = ClientContext.init();
		BikeControlSender sender = new BikeControlSender(ctx.inputManager);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, VoxelVelo.id("bike_hud"), new BikeHud(ctx, sender));
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, VoxelVelo.id("heart_rate_hud"), new HeartRateHud(ctx));
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, VoxelVelo.id("stats_overlay"), new StatsOverlay(ctx));
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, VoxelVelo.id("junction_prompt"), new JunctionPrompt(ctx));
		ClientPlayNetworking.registerGlobalReceiver(RiderStatsPayload.TYPE, (payload, context) -> {
			if (payload.isSupported()) {
				ctx.riderStats.update(payload);
			}
		});
		ClientPlayConnectionEvents.JOIN.register((handler, packetSender, minecraft) -> {
			ctx.sendRiderSettings();
			ctx.riderStats.clear();
			ctx.vitalsSender.reset();
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ctx.bluetooth.tick();
			if (client.level != null) {
				ctx.ftms.tick(ctx.config);
				ctx.vitalsSender.tick();
				ctx.obc.tick(ctx.config);
			}
			sender.tick(client);
			ctx.rideTerrain.tick(client);
			ctx.trainerFeedback.tick(client);
			openConfigOnKey(client);
			toggleStatsOnKey(client, ctx);
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			ctx.config.save();
			ctx.trainerFeedback.shutdown();
			ctx.obc.shutdown();
			ctx.bluetooth.shutdown();
		});
	}

	private static void toggleStatsOnKey(Minecraft client, ClientContext ctx) {
		boolean pressed = false;
		while (VoxelVeloKeys.TOGGLE_STATS.consumeClick()) {
			pressed = true;
		}
		if (pressed && client.gui.screen() == null) {
			ctx.config.statsOverlayEnabled = !ctx.config.statsOverlayEnabled;
			ctx.config.save();
			if (client.player != null) {
				client.player.sendOverlayMessage(Component.translatable(ctx.config.statsOverlayEnabled ? "voxelvelo.stats.shown" : "voxelvelo.stats.hidden"));
			}
		}
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
