package dev.michidk.voxelvelo.client.fitness;

import dev.michidk.voxelvelo.VoxelVelo;
import dev.michidk.voxelvelo.client.ClientContext;
import dev.michidk.voxelvelo.client.ext.VoxelVeloClientExtension;
import dev.michidk.voxelvelo.client.hud.HeartRateHud;
import dev.michidk.voxelvelo.client.hud.JunctionPrompt;
import dev.michidk.voxelvelo.client.hud.StatsOverlay;
import dev.michidk.voxelvelo.client.input.ControlChannel;
import dev.michidk.voxelvelo.client.input.VoxelVeloKeys;
import dev.michidk.voxelvelo.client.ui.ObcScreen;
import dev.michidk.voxelvelo.client.ui.PowerMeterScreen;
import dev.michidk.voxelvelo.client.ui.RoadScreen;
import dev.michidk.voxelvelo.client.ui.SourcesScreen;
import dev.michidk.voxelvelo.client.ui.TrainerScreen;
import dev.michidk.voxelvelo.network.RiderStatsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Plugs the fitness integration into the client: FTMS trainers and power meters over Bluetooth, resistance
 * feedback, OpenBikeControl devices, hands-free road following, and sharing of watts and heart rate.
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

		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, VoxelVelo.id("heart_rate_hud"), new HeartRateHud(ctx));
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, VoxelVelo.id("stats_overlay"), new StatsOverlay(ctx));
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, VoxelVelo.id("junction_prompt"), new JunctionPrompt(ctx));
		ClientPlayNetworking.registerGlobalReceiver(RiderStatsPayload.TYPE, (payload, context) -> {
			if (payload.isSupported()) {
				ctx.riderStats.update(payload);
			}
		});
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
		toggleStatsOnKey(client);
	}

	@Override
	public void onJoin() {
		this.ctx.riderStats.clear();
		this.ctx.vitalsSender.reset();
	}

	@Override
	public void onStopping() {
		this.ctx.config.save();
		this.ctx.trainerFeedback.shutdown();
		this.ctx.obc.shutdown();
		this.ctx.bluetooth.shutdown();
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
