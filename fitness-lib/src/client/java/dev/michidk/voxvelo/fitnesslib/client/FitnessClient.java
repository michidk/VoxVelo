package dev.michidk.voxvelo.fitnesslib.client;

import dev.michidk.voxvelo.fitnesslib.client.fitness.FitnessKeys;
import dev.michidk.voxvelo.fitnesslib.client.hud.BikeHud;
import dev.michidk.voxvelo.fitnesslib.client.hud.HeartRateHud;
import dev.michidk.voxvelo.fitnesslib.client.hud.SteeringHud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

public final class FitnessClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		FitnessRuntime runtime = FitnessRuntime.get();
		DeviceNotifications notifications = new DeviceNotifications();
		FitnessKeys.register();
dev.michidk.voxvelo.fitnesslib.client.obc.ObcActions.register(0x24, "key.voxvelo_fitness_lib.photo", FitnessKeys.action("key.voxvelo_fitness_lib.photo"), dev.michidk.voxvelo.fitnesslib.client.ride.RidePhotos::request);
dev.michidk.voxvelo.fitnesslib.client.obc.ObcActions.register(0x80, "key.voxvelo_fitness_lib.toggle_recording", FitnessKeys.TOGGLE_RECORDING, () -> {
if (runtime.rideRecorder.isRecording()) runtime.saveRide(); else runtime.rideRecorder.start();
});
dev.michidk.voxvelo.fitnesslib.client.obc.ObcActions.register(0x81, "key.voxvelo_fitness_lib.start_recording", FitnessKeys.action("key.voxvelo_fitness_lib.start_recording"), () -> {
if (!runtime.rideRecorder.isRecording()) runtime.rideRecorder.start();
});
dev.michidk.voxvelo.fitnesslib.client.obc.ObcActions.register(0x82, "key.voxvelo_fitness_lib.stop_recording", FitnessKeys.action("key.voxvelo_fitness_lib.stop_recording"), () -> {
if (runtime.rideRecorder.isRecording()) runtime.saveRide();
});
dev.michidk.voxvelo.fitnesslib.client.obc.ObcActions.register(0x86, "key.voxvelo_fitness_lib.settings", FitnessKeys.OPEN_SETTINGS, () -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(new FitnessSettingsScreen(null)));
dev.michidk.voxvelo.fitnesslib.client.obc.ObcActions.register(0x40, "key.voxvelo_fitness_lib.camera", FitnessKeys.action("key.voxvelo_fitness_lib.camera"), () -> {
var client = net.minecraft.client.Minecraft.getInstance(); client.options.setCameraType(client.options.getCameraType().cycle());
});
dev.michidk.voxvelo.fitnesslib.client.obc.ObcActions.register(0x44, "key.voxvelo_fitness_lib.hud", FitnessKeys.action("key.voxvelo_fitness_lib.hud"), () -> net.minecraft.client.Minecraft.getInstance().gui.hud.toggle());
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
			Identifier.fromNamespaceAndPath(FitnessRuntime.MOD_ID, "steering_hud"), new SteeringHud(runtime));
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
					notifications.tick(client, runtime);
dev.michidk.voxvelo.fitnesslib.client.obc.ObcActions.tick(runtime.obc.controls());
				});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> runtime.finishRide());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> runtime.shutdown());
	}
}
