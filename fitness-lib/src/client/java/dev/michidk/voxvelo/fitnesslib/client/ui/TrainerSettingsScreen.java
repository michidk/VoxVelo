package dev.michidk.voxvelo.fitnesslib.client.ui;

import dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime;
import dev.michidk.voxvelo.fitnesslib.client.fitness.FitnessStatus;
import dev.michidk.voxvelo.fitnesslib.client.ftms.TrainerLimits;
import java.util.Locale;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** How the game talks to the connected trainer: resistance from the terrain, virtual gears and the physical bike. */
public class TrainerSettingsScreen extends SettingsScreen {
	private static final int HALF = SettingsList.HALF;
	private static final int RIGHT = SettingsList.WIDTH - HALF;

	private final FitnessRuntime ctx = FitnessRuntime.get();

	public TrainerSettingsScreen(Screen parent) {
		super(parent, Component.translatable("voxvelo_fitness_lib.trainer_settings.title"));
	}

	@Override
	protected void addOptions() {
		this.rows.addText(() -> FitnessStatus.trainerStatus(this.ctx), STATUS);
		this.rows.addRow(CycleButton.onOffBuilder(this.ctx.config.trainerResistanceEnabled)
				.withTooltip(value -> Tips.of("voxvelo_fitness_lib.config.resistance"))
				.create(0, 0, HALF, 20, Component.translatable("voxvelo_fitness_lib.config.resistance"), (button, value) -> this.ctx.config.trainerResistanceEnabled = value),
			new ValueSlider(RIGHT, 0, HALF, TrainerLimits.MIN_INTENSITY, TrainerLimits.MAX_INTENSITY, 0.1, this.ctx.config.trainerResistanceIntensity,
				v -> Component.translatable("voxvelo_fitness_lib.config.intensity", Math.round(v * 100)), v -> this.ctx.config.trainerResistanceIntensity = v).tip("voxvelo_fitness_lib.config.intensity"));
		this.rows.addRow(CycleButton.onOffBuilder(this.ctx.config.virtualShiftingEnabled)
			.withTooltip(value -> Tips.of("voxvelo_fitness_lib.config.virtual_shifting"))
			.create(0, 0, SettingsList.WIDTH, 20, Component.translatable("voxvelo_fitness_lib.config.virtual_shifting"),
				(button, value) -> this.ctx.config.virtualShiftingEnabled = value));
		this.rows.addRow(new ValueSlider(0, 0, HALF, TrainerLimits.MIN_GEAR_RATIO, TrainerLimits.MAX_GEAR_RATIO, 0.05, this.ctx.config.trainerGearRatio,
				v -> Component.translatable("voxvelo_fitness_lib.config.physical_ratio", String.format(Locale.ROOT, "%.2f", v)),
				v -> this.ctx.config.trainerGearRatio = v).tip("voxvelo_fitness_lib.config.physical_ratio"),
			new ValueSlider(RIGHT, 0, HALF, TrainerLimits.MIN_WHEEL_CIRCUMFERENCE_M, TrainerLimits.MAX_WHEEL_CIRCUMFERENCE_M, 0.05, this.ctx.config.trainerWheelCircumferenceM,
				v -> Component.translatable("voxvelo_fitness_lib.config.wheel_circumference", String.format(Locale.ROOT, "%.2f", v)),
				v -> this.ctx.config.trainerWheelCircumferenceM = v).tip("voxvelo_fitness_lib.config.wheel_circumference"));
	}

	@Override
	protected void save() {
		this.ctx.config.save();
	}
}
