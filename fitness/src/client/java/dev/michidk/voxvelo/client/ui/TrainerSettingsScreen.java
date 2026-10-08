package dev.michidk.voxvelo.client.ui;

import dev.michidk.voxelfitness.client.FitnessRuntime;
import dev.michidk.voxelfitness.client.ui.Tips;
import dev.michidk.voxelfitness.client.ui.ValueSlider;
import dev.michidk.voxvelo.client.fitness.FitnessStatus;
import dev.michidk.voxvelo.client.ftms.TrainerLimits;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** How the game talks to the connected trainer: resistance from the terrain, virtual gears and the physical bike. */
public class TrainerSettingsScreen extends Screen {
	private static final int WIDTH = 310;

	private final Screen parent;
	private final FitnessRuntime ctx = FitnessRuntime.get();

	public TrainerSettingsScreen(Screen parent) {
		super(Component.translatable("voxvelo.trainer_settings.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - WIDTH / 2;
		int half = (WIDTH - 10) / 2;
		int right = left + half + 10;
		int y = 60;

		this.addRenderableWidget(CycleButton.onOffBuilder(this.ctx.config.trainerResistanceEnabled)
			.withTooltip(value -> Tips.of("voxvelo.config.resistance"))
			.create(left, y, half, 20, Component.translatable("voxvelo.config.resistance"), (button, value) -> this.ctx.config.trainerResistanceEnabled = value));
		this.addRenderableWidget(new ValueSlider(right, y, half, TrainerLimits.MIN_INTENSITY, TrainerLimits.MAX_INTENSITY, 0.1, this.ctx.config.trainerResistanceIntensity,
			v -> Component.translatable("voxvelo.config.intensity", Math.round(v * 100)), v -> this.ctx.config.trainerResistanceIntensity = v).tip("voxvelo.config.intensity"));
		y += 24;
		this.addRenderableWidget(CycleButton.onOffBuilder(this.ctx.config.virtualShiftingEnabled)
			.withTooltip(value -> Tips.of("voxvelo.config.virtual_shifting"))
			.create(left, y, WIDTH, 20, Component.translatable("voxvelo.config.virtual_shifting"),
				(button, value) -> this.ctx.config.virtualShiftingEnabled = value));
		y += 24;
		this.addRenderableWidget(new ValueSlider(left, y, half, TrainerLimits.MIN_GEAR_RATIO, TrainerLimits.MAX_GEAR_RATIO, 0.05, this.ctx.config.trainerGearRatio,
			v -> Component.translatable("voxvelo.config.physical_ratio", String.format(java.util.Locale.ROOT, "%.2f", v)),
			v -> this.ctx.config.trainerGearRatio = v).tip("voxvelo.config.physical_ratio"));
		this.addRenderableWidget(new ValueSlider(right, y, half, TrainerLimits.MIN_WHEEL_CIRCUMFERENCE_M, TrainerLimits.MAX_WHEEL_CIRCUMFERENCE_M, 0.05, this.ctx.config.trainerWheelCircumferenceM,
			v -> Component.translatable("voxvelo.config.wheel_circumference", String.format(java.util.Locale.ROOT, "%.2f", v)),
			v -> this.ctx.config.trainerWheelCircumferenceM = v).tip("voxvelo.config.wheel_circumference"));

		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
			.bounds(left, this.height - 30, WIDTH, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int cx = this.width / 2;
		graphics.centeredText(this.font, this.title, cx, 20, 0xFFFFFFFF);
		graphics.centeredText(this.font, FitnessStatus.trainerStatus(this.ctx), cx, 36, 0xFFC0C0C0);
	}

	@Override
	public void onClose() {
		this.ctx.config.save();
		this.minecraft.gui.setScreen(this.parent);
	}
}
