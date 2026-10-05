package dev.michidk.voxelvelo.client.ui;

import dev.michidk.voxelvelo.client.ClientContext;
import dev.michidk.voxelvelo.client.ftms.TrainerState;
import dev.michidk.voxelvelo.client.ftms.TrainerTelemetry;
import dev.michidk.voxelvelo.client.input.BikeInputSource;
import dev.michidk.voxelvelo.client.input.ControlChannel;
import java.util.ArrayList;
import java.util.List;
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
		int y = 64;

		this.addRenderableWidget(Button.builder(Component.translatable("voxelvelo.config.choose_trainer"),
			button -> this.minecraft.gui.setScreen(new TrainerScreen(this))).bounds(left, y, WIDTH, 20).build());
		y += 24;

		this.addRenderableWidget(CycleButton.onOffBuilder(this.ctx.config.autoConnectTrainer)
			.create(left, y, WIDTH, 20, Component.translatable("voxelvelo.config.auto_connect"), (button, value) -> this.ctx.config.autoConnectTrainer = value));
		y += 24;

		List<BikeInputSource> choices = new ArrayList<>();
		for (BikeInputSource source : this.ctx.inputManager.sources()) {
			if (source.supports(ControlChannel.PROPULSION)) {
				choices.add(source);
			}
		}
		this.addRenderableWidget(CycleButton.<BikeInputSource>builder(source -> Component.literal(source.displayName()), this.ctx.inputManager.selected(ControlChannel.PROPULSION))
			.withValues(choices)
			.create(left, y, WIDTH, 20, Component.translatable("voxelvelo.config.propulsion"), (button, value) -> this.ctx.config.setSource(ControlChannel.PROPULSION, value.id())));
		y += 24;

		this.addRenderableWidget(new PowerSlider(left, y));
		y += 24;
		this.addRenderableWidget(new SensitivitySlider(left, y));

		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
			.bounds(left, this.height - 30, WIDTH, 20).build());
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
	}

	@Override
	public void onClose() {
		this.ctx.config.save();
		this.minecraft.gui.setScreen(this.parent);
	}

	private final class PowerSlider extends AbstractSliderButton {
		PowerSlider(int x, int y) {
			super(x, y, WIDTH, 20, Component.empty(), (ConfigScreen.this.ctx.config.keyboardVirtualPower - MIN_POWER) / (MAX_POWER - MIN_POWER));
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
		SensitivitySlider(int x, int y) {
			super(x, y, WIDTH, 20, Component.empty(), (ConfigScreen.this.ctx.config.keyboardSteeringSensitivity - MIN_SENSITIVITY) / (1.0 - MIN_SENSITIVITY));
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
}
