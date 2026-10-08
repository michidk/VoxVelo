package dev.michidk.voxvelo.client.fitness;

import dev.michidk.voxelfitness.api.Fitness;
import dev.michidk.voxelfitness.api.SettingsPage;
import dev.michidk.voxvelo.VoxVelo;
import dev.michidk.voxvelo.client.ClientContext;
import dev.michidk.voxvelo.client.ext.VoxVeloClientExtension;
import dev.michidk.voxvelo.client.hud.JunctionPrompt;
import dev.michidk.voxvelo.client.hud.StatsOverlay;
import dev.michidk.voxvelo.client.ride.BicycleRideExport;
import dev.michidk.voxvelo.client.ride.RideExport;
import dev.michidk.voxvelo.client.ui.RoadScreen;
import dev.michidk.voxvelo.network.RiderStatsPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Plugs the fitness integration into the client: FTMS trainers, power meters and heart rate sensors
 * over Bluetooth, resistance feedback, OpenBikeControl devices, hands-free road following, sharing
 * of watts and heart rate, and ride recording with FIT export and ride maps.
 */
public final class FitnessClientExtension implements VoxVeloClientExtension {
	private FitnessContext ctx;

	@Override
	public void init(ClientContext core) {
		FitnessContext ctx = FitnessContext.init(core);
		this.ctx = ctx;
		RideExport.registerMapExporter(
				BicycleRideExport::canRequestMap, BicycleRideExport::requestMap);
		FitnessKeys.register();

		core.statusLines()
				.add(
						() ->
								FitnessStatus.trainerStatus(
										dev.michidk.voxelfitness.client.FitnessRuntime.get()));
		core.statusLines()
				.add(
						() ->
								FitnessStatus.powerMeterStatus(
										dev.michidk.voxelfitness.client.FitnessRuntime.get()));
		core.statusLines()
				.add(
						() ->
								FitnessStatus.heartRateStatus(
										dev.michidk.voxelfitness.client.FitnessRuntime.get()));
		core.statusLines()
				.add(
						() ->
								FitnessStatus.telemetryLine(
										dev.michidk.voxelfitness.client.FitnessRuntime.get()));
		core.statusLines()
				.add(
						() ->
								FitnessStatus.obcStatus(
										dev.michidk.voxelfitness.client.FitnessRuntime.get()));
		// The bicycle pages join the fitness settings, and the bike settings list every fitness page, so both
		// menus lead to the same pages.
		Fitness.addSettingsPage("voxvelo.config.road", RoadScreen::new);
		Fitness.addSettingsPage("voxvelo.config.stats", StatsScreen::new);
		for (SettingsPage page : Fitness.settingsPages()) {
			core.settingsPages()
					.add(
							new ClientContext.SettingsPage(
									page.labelKey(), page.factory(), "voxvelo.config.section.fitness"));
		}

		HudElementRegistry.attachElementAfter(
				VanillaHudElements.HOTBAR, VoxVelo.id("stats_overlay"), new StatsOverlay(ctx));
		HudElementRegistry.attachElementAfter(
				VanillaHudElements.HOTBAR,
				VoxVelo.id("junction_prompt"),
				new JunctionPrompt(ctx));
		ClientPlayNetworking.registerGlobalReceiver(
				RiderStatsPayload.TYPE,
				(payload, context) -> {
					if (payload.isSupported()) {
						ctx.riderStats.update(payload);
					}
				});
	}

	@Override
	public void tick(Minecraft client) {
		FitnessContext ctx = this.ctx;
		if (client.level != null) ctx.vitalsSender.tick();
		toggleStatsOnKey(client);
	}

	@Override
	public void onJoin() {
		this.ctx.riderStats.clear();
		this.ctx.vitalsSender.reset();
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
				client.player.sendOverlayMessage(
						Component.translatable(
								this.ctx.config.statsOverlayEnabled
										? "voxvelo.stats.shown"
										: "voxvelo.stats.hidden"));
			}
		}
	}
}
