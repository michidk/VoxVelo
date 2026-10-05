package dev.michidk.voxelvelo.client.ui;

import dev.michidk.voxelvelo.client.ClientContext;
import dev.michidk.voxelvelo.client.ftms.TrainerState;
import dev.michidk.voxelvelo.client.ftms.TrainerTelemetry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The VoxelVelo settings hub, opened with the "Open Bike Settings" key. It shows the connection status of the
 * trainer and the controller and leads to one page per topic.
 */
public class ConfigScreen extends Screen {
	private static final int WIDTH = 310;

	private final @Nullable Screen parent;
	private final ClientContext ctx = ClientContext.get();

	public ConfigScreen(@Nullable Screen parent) {
		super(Component.translatable("voxelvelo.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - WIDTH / 2;
		int half = (WIDTH - 10) / 2;
		int right = left + half + 10;
		int y = 76;

		this.page(left, y, half, "voxelvelo.config.trainer_page", () -> new TrainerScreen(this));
		this.page(right, y, half, "voxelvelo.config.obc", () -> new ObcScreen(this));
		y += 24;
		this.page(left, y, half, "voxelvelo.config.sources", () -> new SourcesScreen(this));
		this.page(right, y, half, "voxelvelo.config.road", () -> new RoadScreen(this));
		y += 24;
		this.page(left, y, WIDTH, "voxelvelo.config.rider", () -> new RiderScreen(this));

		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
			.bounds(left, this.height - 30, WIDTH, 20).build());
	}

	/** Adds a button that opens another settings page, built when the button is pressed. */
	private void page(int x, int y, int width, String labelKey, java.util.function.Supplier<Screen> target) {
		this.addRenderableWidget(Button.builder(Component.translatable(labelKey), button -> this.minecraft.gui.setScreen(target.get()))
			.bounds(x, y, width, 20).build());
	}

	/** One-line trainer status shared with the other pages. */
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

	/** One-line OpenBikeControl status, shared with the picker and sources pages. */
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
}
