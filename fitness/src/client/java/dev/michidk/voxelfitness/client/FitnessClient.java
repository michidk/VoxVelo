package dev.michidk.voxelfitness.client;

import dev.michidk.voxvelo.client.fitness.FitnessKeys;
import dev.michidk.voxvelo.client.hud.BikeHud;
import dev.michidk.voxvelo.client.hud.HeartRateHud;
import dev.michidk.voxvelo.client.ride.RideScreen;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class FitnessClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		FitnessRuntime runtime = FitnessRuntime.get();
		FitnessKeys.register();
		HudElementRegistry.attachElementAfter(
				VanillaHudElements.HOTBAR,
				Identifier.fromNamespaceAndPath(FitnessRuntime.MOD_ID, "ride_hud"),
				new BikeHud(runtime));
		HudElementRegistry.attachElementAfter(
				VanillaHudElements.HOTBAR,
				Identifier.fromNamespaceAndPath(FitnessRuntime.MOD_ID, "heart_rate_hud"),
				new HeartRateHud(runtime));
		ClientTickEvents.END_CLIENT_TICK.register(
				client -> {
					runtime.tick(client);
					boolean settings = false;
					while (FitnessKeys.OPEN_SETTINGS.consumeClick()) settings = true;
					if (settings && client.gui.screen() == null)
						client.gui.setScreen(new FitnessSettingsScreen(null));
					boolean record = false;
					while (FitnessKeys.TOGGLE_RECORDING.consumeClick()) record = true;
					if (record && client.gui.screen() == null) {
						if (runtime.rideRecorder.isRecording()) {
							runtime.rideRecorder.stop();
							client.gui.setScreen(new RideScreen(null));
						} else {
							runtime.rideRecorder.start();
							if (client.player != null)
								client.player.sendOverlayMessage(
										Component.translatable(
												"voxvelo.ride.started",
												FitnessKeys.TOGGLE_RECORDING
														.getTranslatedKeyMessage()));
						}
					}
				});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> runtime.finishRide());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> runtime.shutdown());
	}
}
