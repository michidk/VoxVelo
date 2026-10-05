package dev.michidk.voxelvelo.client.ui;

import dev.michidk.voxelvelo.client.ClientContext;
import dev.michidk.voxelvelo.client.ftms.TrainerState;
import dev.michidk.voxelvelo.client.ftms.TrainerTelemetry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Main VoxelVelo settings screen. Opened with the "Open Bike Settings" key. */
public class ConfigScreen extends Screen {
	private static final int WIDTH = 310;
	private static final double MIN_POWER = 50.0;
	private static final double MAX_POWER = 600.0;
	private static final double MIN_SENSITIVITY = 0.3;

	private final @Nullable Screen parent;
	private final ClientContext ctx = ClientContext.get();

	public ConfigScreen(@Nullable Screen parent) {
		super(Component.translatable("voxelvelo.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - WIDTH / 2;
		int y = 76;
		int half = (WIDTH - 10) / 2;
		int right = left + half + 10;

		this.addRenderableWidget(Button.builder(Component.translatable("voxelvelo.config.choose_trainer"),
			button -> this.minecraft.gui.setScreen(new TrainerScreen(this))).bounds(left, y, half, 20).build());
		this.addRenderableWidget(CycleButton.onOffBuilder(this.ctx.config.autoConnectTrainer)
			.create(right, y, half, 20, Component.translatable("voxelvelo.config.auto_connect"), (button, value) -> this.ctx.config.autoConnectTrainer = value));
		y += 24;
		this.addRenderableWidget(Button.builder(Component.translatable("voxelvelo.config.obc"),
			button -> this.minecraft.gui.setScreen(new ObcScreen(this))).bounds(left, y, half, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("voxelvelo.config.sources"),
			button -> this.minecraft.gui.setScreen(new SourcesScreen(this))).bounds(right, y, half, 20).build());
		y += 24;
		this.addRenderableWidget(CycleButton.onOffBuilder(this.ctx.config.trainerResistanceEnabled)
			.create(left, y, half, 20, Component.translatable("voxelvelo.config.resistance"), (button, value) -> this.ctx.config.trainerResistanceEnabled = value));
		this.addRenderableWidget(new IntensitySlider(right, y, half));
		y += 24;
		this.addRenderableWidget(new PowerSlider(left, y, WIDTH));
		y += 24;
		this.addRenderableWidget(new SensitivitySlider(left, y, WIDTH));

		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
			.bounds(left, this.height - 30, WIDTH, 20).build());
	}

	/** One-line OpenBikeControl status, shared with the picker and sources screens. */
	static Component obcStatus(ClientContext ctx) {
		String name = ctx.obc.deviceName();
		if (name.isEmpty()) {
			name = ctx.config.obcPreferredName;
		}
		if (name.isEmpty()) {
			return Component.translatable("voxelvelo.config.obc.none");
		}
		TrainerState state = ctx.obc.state();
		String stateKey = switch (state) {
			case CONNECTED -> "voxelvelo.config.trainer.connected";
			case CONNECTING -> "voxelvelo.config.trainer.connecting";
			case DISCONNECTED -> "voxelvelo.config.trainer.disconnected";
		};
		Component line = Component.translatable("voxelvelo.config.obc.status", name, Component.translatable(stateKey));
		String message = ctx.obc.message();
		return state != TrainerState.CONNECTED && !message.isEmpty() ? line.copy().append(" - " + message) : line;
	}

	/** One-line trainer status shared with the trainer picker. */
	static Component trainerStatus(ClientContext ctx) {
		String name = ctx.ftms.deviceName();
		if (name.isEmpty()) {
			name = ctx.config.preferredTrainerName;
		}
		if (name.isEmpty()) {
			return Component.translatable("voxelvelo.config.trainer.none");
		}
		TrainerState state = ctx.ftms.state();
		String stateKey = switch (state) {
			case CONNECTED -> "voxelvelo.config.trainer.connected";
			case CONNECTING -> "voxelvelo.config.trainer.connecting";
			case DISCONNECTED -> "voxelvelo.config.trainer.disconnected";
		};
		Component line = Component.translatable("voxelvelo.config.trainer", name, Component.translatable(stateKey));
		String message = ctx.ftms.message();
		return state != TrainerState.CONNECTED && !message.isEmpty() ? line.copy().append(" - " + message) : line;
	}

	private Component telemetryLine() {
		TrainerTelemetry telemetry = this.ctx.ftms.freshTelemetry();
		if (telemetry == null) {
			return Component.translatable("voxelvelo.config.telemetry", "--", "--", "--");
		}
		String cadence = telemetry.cadenceRpm() == null ? "--" : String.valueOf(Math.round(telemetry.cadenceRpm()));
		String heart = telemetry.heartRate() == null ? "--" : String.valueOf(telemetry.heartRate());
		return Component.translatable("voxelvelo.config.telemetry", telemetry.powerWatts(), cadence, heart);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int cx = this.width / 2;
		graphics.centeredText(this.font, this.title, cx, 20, 0xFFFFFFFF);
		graphics.centeredText(this.font, trainerStatus(this.ctx), cx, 36, 0xFFC0C0C0);
		graphics.centeredText(this.font, this.telemetryLine(), cx, 48, 0xFF909090);
		graphics.centeredText(this.font, obcStatus(this.ctx), cx, 60, 0xFFC0C0C0);
	}

	@Override
	public void onClose() {
		this.ctx.config.save();
		this.minecraft.gui.setScreen(this.parent);
	}

	private final class PowerSlider extends AbstractSliderButton {
		PowerSlider(int x, int y, int width) {
			super(x, y, width, 20, Component.empty(), (ConfigScreen.this.ctx.config.keyboardVirtualPower - MIN_POWER) / (MAX_POWER - MIN_POWER));
			this.updateMessage();
		}

		private double power() {
			return Math.round((MIN_POWER + this.value * (MAX_POWER - MIN_POWER)) / 10.0) * 10.0;
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.translatable("voxelvelo.config.virtual_power", Math.round(this.power())));
		}

		@Override
		protected void applyValue() {
			ConfigScreen.this.ctx.config.keyboardVirtualPower = this.power();
		}
	}

	private final class SensitivitySlider extends AbstractSliderButton {
		SensitivitySlider(int x, int y, int width) {
			super(x, y, width, 20, Component.empty(), (ConfigScreen.this.ctx.config.keyboardSteeringSensitivity - MIN_SENSITIVITY) / (1.0 - MIN_SENSITIVITY));
			this.updateMessage();
		}

		private double sensitivity() {
			return Math.round((MIN_SENSITIVITY + this.value * (1.0 - MIN_SENSITIVITY)) * 20.0) / 20.0;
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.translatable("voxelvelo.config.steering_sensitivity", Math.round(this.sensitivity() * 100)));
		}

		@Override
		protected void applyValue() {
			ConfigScreen.this.ctx.config.keyboardSteeringSensitivity = this.sensitivity();
		}
	}

	/** How strongly game terrain is turned into trainer resistance: 0 to 200 percent. */
	private final class IntensitySlider extends AbstractSliderButton {
		IntensitySlider(int x, int y, int width) {
			super(x, y, width, 20, Component.empty(), ConfigScreen.this.ctx.config.trainerResistanceIntensity / 2.0);
			this.updateMessage();
		}

		private double intensity() {
			return Math.round(this.value * 2.0 * 10.0) / 10.0;
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.translatable("voxelvelo.config.intensity", Math.round(this.intensity() * 100)));
		}

		@Override
		protected void applyValue() {
			ConfigScreen.this.ctx.config.trainerResistanceIntensity = this.intensity();
		}
	}
}
