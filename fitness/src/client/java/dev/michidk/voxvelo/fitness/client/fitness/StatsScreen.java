package dev.michidk.voxvelo.fitness.client.fitness;

import dev.michidk.voxvelo.bikes.client.ui.SettingsList;
import dev.michidk.voxvelo.bikes.client.ui.SettingsScreen;
import dev.michidk.voxvelo.bikes.client.ui.Tips;
import dev.michidk.voxvelo.fitnesslib.client.fitness.HudVisibility;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The riding HUD parts, the rider stats overlay, and what this player shares with the others. */
public class StatsScreen extends SettingsScreen {
	private static final int RIGHT = SettingsList.WIDTH - SettingsList.HALF;

	private final FitnessContext ctx = FitnessContext.get();

	public StatsScreen(Screen parent) {
		super(parent, Component.translatable("voxvelo_fitness_lib.stats.title"));
	}

	@Override
	protected void addOptions() {
		this.rows.addHeader(Component.translatable("voxvelo_fitness_lib.stats.section.hud"));
		this.rows.addRow(
			this.visibility(0, "voxvelo_fitness_lib.rider.hud", this.ctx.config.ridingHudVisibility, value -> this.ctx.config.ridingHudVisibility = value),
			this.visibility(RIGHT, "voxvelo_fitness_lib.rider.heart_graph", this.ctx.config.heartHudVisibility, value -> this.ctx.config.heartHudVisibility = value));
		this.rows.addRow(
			this.toggle(0, "voxvelo_fitness_lib.rider.stats", this.ctx.config.statsOverlayEnabled, value -> this.ctx.config.statsOverlayEnabled = value),
			this.visibility(RIGHT, "voxvelo_fitness_lib.rider.junction_hud", this.ctx.config.junctionHudVisibility, value -> this.ctx.config.junctionHudVisibility = value));
		this.rows.addRow(this.visibility(0, "voxvelo_fitness_lib.rider.steering_hud", this.ctx.config.steeringHudVisibility,
			value -> this.ctx.config.steeringHudVisibility = value),
			this.toggle(RIGHT, "voxvelo_fitness_lib.trainer.debug", this.ctx.config.trainerDebugHudEnabled, value -> this.ctx.config.trainerDebugHudEnabled = value));
		this.rows.addHeader(Component.translatable("voxvelo_fitness_lib.stats.section.sharing"));
		this.rows.addRow(
			this.toggle(0, "voxvelo_fitness_lib.rider.share_watts", this.ctx.config.shareWatts, value -> this.ctx.config.shareWatts = value),
			this.toggle(RIGHT, "voxvelo_fitness_lib.rider.share_hr", this.ctx.config.shareHeartRate, value -> this.ctx.config.shareHeartRate = value));
	}

	private AbstractWidget visibility(int x, String key, HudVisibility value, Consumer<HudVisibility> change) {
		return CycleButton.builder(HudVisibility::label, value).withValues(HudVisibility.values()).withTooltip(mode -> Tips.of(key))
			.create(x, 0, SettingsList.HALF, 20, Component.translatable(key), (button, mode) -> change.accept(mode));
	}

	private AbstractWidget toggle(int x, String key, boolean enabled, Consumer<Boolean> change) {
		return CycleButton.onOffBuilder(enabled)
			.withTooltip(value -> Tips.of(key))
			.create(x, 0, SettingsList.HALF, 20, Component.translatable(key), (button, value) -> change.accept(value));
	}

	@Override
	protected void save() {
		this.ctx.config.save();
	}
}
