package dev.michidk.voxelvelo.client.fitness;

import dev.michidk.voxelvelo.VoxelVelo;
import dev.michidk.voxelvelo.client.ClientContext;
import dev.michidk.voxelvelo.client.ext.VoxelVeloClientExtension;
import dev.michidk.voxelvelo.client.hud.HeartRateHud;
import dev.michidk.voxelvelo.client.hud.JunctionPrompt;
import dev.michidk.voxelvelo.client.hud.StatsOverlay;
import dev.michidk.voxelvelo.client.input.ControlChannel;
import dev.michidk.voxelvelo.client.input.VoxelVeloKeys;
import dev.michidk.voxelvelo.client.ride.RideExport;
import dev.michidk.voxelvelo.client.ride.RideRecorder;
import dev.michidk.voxelvelo.client.ride.RideRecording;
import dev.michidk.voxelvelo.client.ride.RideScreen;
import dev.michidk.voxelvelo.client.ui.ObcScreen;
import dev.michidk.voxelvelo.client.ui.PowerMeterScreen;
import dev.michidk.voxelvelo.client.ui.RoadScreen;
import dev.michidk.voxelvelo.client.ui.SourcesScreen;
import dev.michidk.voxelvelo.client.ui.TrainerScreen;
import dev.michidk.voxelvelo.network.RiderStatsPayload;
import java.io.IOException;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Plugs the fitness integration into the client: FTMS trainers and power meters over Bluetooth, resistance
 * feedback, OpenBikeControl devices, hands-free road following, sharing of watts and heart rate, and ride recording
 * with FIT export and ride maps.
 */
public final class FitnessClientExtension implements VoxelVeloClientExtension {
	private FitnessContext ctx;

	@Override
	public void init(ClientContext core) {
		FitnessContext ctx = FitnessContext.init(core);
		this.ctx = ctx;
		FitnessKeys.register();

		core.hudExtras().add(new FitnessHudExtras(ctx));
		core.statusLines().add(() -> FitnessStatus.trainerStatus(ctx));
		core.statusLines().add(() -> FitnessStatus.powerMeterStatus(ctx));
		core.statusLines().add(() -> FitnessStatus.telemetryLine(ctx));
		core.statusLines().add(() -> FitnessStatus.obcStatus(ctx));
		// Riding off without a connected trainer means coasting; say so, and where the settings are.
		core.mountListeners().add(player -> {
			if (!ctx.inputManager.selected(ControlChannel.PROPULSION).isActive()) {
				player.sendOverlayMessage(Component.translatable("voxelvelo.hint.trainer_not_connected", VoxelVeloKeys.OPEN_CONFIG.getTranslatedKeyMessage()));
			}
		});
		core.settingsPages().add(new ClientContext.SettingsPage("voxelvelo.config.trainer_page", TrainerScreen::new));
		core.settingsPages().add(new ClientContext.SettingsPage("voxelvelo.config.power_meter_page", PowerMeterScreen::new));
		core.settingsPages().add(new ClientContext.SettingsPage("voxelvelo.config.obc", ObcScreen::new));
		core.settingsPages().add(new ClientContext.SettingsPage("voxelvelo.config.sources", SourcesScreen::new));
		core.settingsPages().add(new ClientContext.SettingsPage("voxelvelo.config.road", RoadScreen::new));
		core.settingsPages().add(new ClientContext.SettingsPage("voxelvelo.config.stats", StatsScreen::new));
		core.settingsPages().add(new ClientContext.SettingsPage("voxelvelo.config.ride_recording", RideScreen::new));

		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, VoxelVelo.id("heart_rate_hud"), new HeartRateHud(ctx));
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, VoxelVelo.id("stats_overlay"), new StatsOverlay(ctx));
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, VoxelVelo.id("junction_prompt"), new JunctionPrompt(ctx));
		ClientPlayNetworking.registerGlobalReceiver(RiderStatsPayload.TYPE, (payload, context) -> {
			if (payload.isSupported()) {
				ctx.riderStats.update(payload);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> this.finishRide());
	}

	@Override
	public void tick(Minecraft client) {
		FitnessContext ctx = this.ctx;
		ctx.bluetooth.tick();
		if (client.level != null) {
			ctx.ftms.tick(ctx.config);
			ctx.powerMeter.tick(ctx.config);
			ctx.vitalsSender.tick();
			ctx.obc.tick(ctx.config);
		}
		ctx.rideTerrain.tick(client);
		ctx.trainerFeedback.tick(client);
		ctx.rideRecorder.tick(client);
		toggleStatsOnKey(client);
		toggleRecordingOnKey(client);
	}

	@Override
	public void onJoin() {
		this.ctx.riderStats.clear();
		this.ctx.vitalsSender.reset();
	}

	@Override
	public void onStopping() {
		this.finishRide();
		this.ctx.config.save();
		this.ctx.trainerFeedback.shutdown();
		this.ctx.obc.shutdown();
		this.ctx.bluetooth.shutdown();
	}

	/** Starts a ride, or stops it and shows its summary. */
	private void toggleRecordingOnKey(Minecraft client) {
		boolean pressed = false;
		while (FitnessKeys.TOGGLE_RECORDING.consumeClick()) {
			pressed = true;
		}
		if (!pressed || client.gui.screen() != null) {
			return;
		}
		RideRecorder recorder = this.ctx.rideRecorder;
		if (recorder.isRecording()) {
			recorder.stop();
			client.gui.setScreen(new RideScreen(null));
		} else {
			recorder.start();
			if (client.player != null) {
				client.player.sendOverlayMessage(Component.translatable("voxelvelo.ride.started", FitnessKeys.TOGGLE_RECORDING.getTranslatedKeyMessage()));
			}
		}
	}

	/**
	 * Leaving the world or closing the game ends a running ride. A ride that was never exported is saved as a FIT
	 * file then, so closing the game never loses it.
	 */
	private void finishRide() {
		RideRecorder recorder = this.ctx.rideRecorder;
		if (recorder.isRecording()) {
			recorder.stop();
		}
		RideRecording ride = recorder.lastRide();
		if (ride == null || ride.isEmpty() || recorder.lastRideSaved()) {
			return;
		}
		try {
			Path file = RideExport.writeFit(ride, this.ctx.config);
			recorder.markLastRideSaved();
			VoxelVelo.LOGGER.info("Saved the unsaved ride to {}", file);
		} catch (IOException e) {
			VoxelVelo.LOGGER.warn("Could not save the unsaved ride", e);
		}
	}

	private void toggleStatsOnKey(Minecraft client) {
		boolean pressed = false;
		while (FitnessKeys.TOGGLE_STATS.consumeClick()) {
			pressed = true;
		}
		if (pressed && client.gui.screen() == null) {
			this.ctx.config.statsOverlayEnabled = !this.ctx.config.statsOverlayEnabled;
			this.ctx.config.save();
			if (client.player != null) {
				client.player.sendOverlayMessage(Component.translatable(this.ctx.config.statsOverlayEnabled ? "voxelvelo.stats.shown" : "voxelvelo.stats.hidden"));
			}
		}
	}
}
