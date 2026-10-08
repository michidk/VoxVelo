package dev.michidk.voxvelo.fitness.client.fitness;

import dev.michidk.voxvelo.bikes.client.ClientContext;
import dev.michidk.voxvelo.bikes.client.ext.VoxVeloClientExtension;
import dev.michidk.voxvelo.fitness.FitnessMod;
import dev.michidk.voxvelo.fitness.client.hud.JunctionPrompt;
import dev.michidk.voxvelo.fitness.client.hud.StatsOverlay;
import dev.michidk.voxvelo.fitness.client.ride.BicycleRideExport;
import dev.michidk.voxvelo.fitness.client.ui.RoadScreen;
import dev.michidk.voxvelo.fitness.network.RiderStatsPayload;
import dev.michidk.voxvelo.fitnesslib.api.Fitness;
import dev.michidk.voxvelo.fitnesslib.api.SettingsPage;
import dev.michidk.voxvelo.fitnesslib.client.fitness.FitnessKeys;
import dev.michidk.voxvelo.fitnesslib.client.fitness.FitnessStatus;
import dev.michidk.voxvelo.fitnesslib.client.ride.RideExport;
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
										dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime.get()));
		core.statusLines()
				.add(
						() ->
								FitnessStatus.powerMeterStatus(
										dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime.get()));
		core.statusLines()
				.add(
						() ->
								FitnessStatus.heartRateStatus(
										dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime.get()));
		core.statusLines()
				.add(
						() ->
								FitnessStatus.telemetryLine(
										dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime.get()));
		core.statusLines()
				.add(
						() ->
								FitnessStatus.obcStatus(
										dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime.get()));
		// The bicycle pages join the fitness settings, and the bike settings list every fitness page, so both
		// menus lead to the same pages.
		Fitness.addSettingsPage("voxvelo_fitness_lib.config.road", RoadScreen::new);
		Fitness.addSettingsPage("voxvelo_fitness_lib.config.stats", StatsScreen::new);
		for (SettingsPage page : Fitness.settingsPages()) {
			core.settingsPages()
					.add(
							new ClientContext.SettingsPage(
									page.labelKey(), page.factory(), "voxvelo_fitness_lib.config.section.fitness"));
		}

		HudElementRegistry.attachElementAfter(
				VanillaHudElements.HOTBAR, FitnessMod.id("stats_overlay"), new StatsOverlay(ctx));
		HudElementRegistry.attachElementAfter(
				VanillaHudElements.HOTBAR,
				FitnessMod.id("junction_prompt"),
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
										? "voxvelo_fitness_lib.stats.shown"
										: "voxvelo_fitness_lib.stats.hidden"));
			}
		}
	}
}
