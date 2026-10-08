package dev.michidk.voxvelo.client.ride;

import com.mojang.blaze3d.Blaze3D;
import dev.michidk.voxelfitness.client.FitnessRuntime;
import dev.michidk.voxelfitness.client.FitnessRuntime;
import dev.michidk.voxelfitness.client.ui.Tips;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Starts and stops ride recording and shows the finished ride: its stats, and the ways out, a FIT file and a map
 * item. Opens by itself when a ride is stopped with the recording key.
 */
public class RideScreen extends Screen {
	private static final int WIDTH = 310;
	private static final int ROW = 12;

	private final @Nullable Screen parent;
	private final FitnessRuntime ctx = FitnessRuntime.get();
	private @Nullable Component message;
	private @Nullable RideRecording shown;
	private List<Component[]> stats = List.of();

	public RideScreen(@Nullable Screen parent) {
		super(Component.translatable("voxvelo.ride.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		RideRecorder recorder = this.ctx.rideRecorder;
		RideRecording ride = recorder.isRecording() ? null : recorder.lastRide();
		if (ride != this.shown) {
			this.shown = ride;
			this.stats = ride == null ? List.of() : statRows(ride.summary());
		}

		int left = this.width / 2 - WIDTH / 2;
		int half = (WIDTH - 10) / 2;
		int right = left + half + 10;
		int y = 48;
		Component toggle = Component.translatable(recorder.isRecording() ? "voxvelo.ride.stop" : "voxvelo.ride.start");
		this.addRenderableWidget(Button.builder(toggle, button -> this.toggleRecording())
			.tooltip(Tips.of(recorder.isRecording() ? "voxvelo.ride.stop" : "voxvelo.ride.start"))
			.bounds(left, y, WIDTH, 20).build());

		y = this.buttonsY();
		Button export = this.addRenderableWidget(Button.builder(Component.translatable("voxvelo.ride.export"), button -> this.export())
			.tooltip(Tips.of("voxvelo.ride.export")).bounds(left, y, half, 20).build());
		Button map = this.addRenderableWidget(Button.builder(Component.translatable("voxvelo.ride.map"), button -> this.requestMap())
			.tooltip(Tips.of("voxvelo.ride.map")).bounds(right, y, half, 20).build());
		export.active = ride != null && !ride.isEmpty();
		map.active = ride != null && !ride.isEmpty() && RideExport.canRequestMap();
		y += 24;
		this.addRenderableWidget(CycleButton.onOffBuilder(this.ctx.config.rideFitPosition)
			.withTooltip(value -> Tips.of("voxvelo.ride.fit_position"))
			.create(left, y, half, 20, Component.translatable("voxvelo.ride.fit_position"),
				(button, value) -> this.ctx.config.rideFitPosition = value));
		this.addRenderableWidget(Button.builder(Component.translatable("voxvelo.ride.open_folder"), button -> this.openFolder())
			.tooltip(Tips.of("voxvelo.ride.open_folder")).bounds(right, y, half, 20).build());

		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
			.bounds(left, this.height - 30, WIDTH, 20).build());
	}

	/** Below the stats, but never over the Done button. */
	private int buttonsY() {
		int statsEnd = this.statsY() + Math.max(1, this.stats.size()) * ROW + 8;
		return Math.min(statsEnd, this.height - 30 - 52);
	}

	private boolean hasUnsavedRide() {
		return !this.ctx.rideRecorder.isRecording() && this.shown != null && !this.shown.isEmpty()
			&& !this.ctx.rideRecorder.lastRideSaved();
	}

	private int statsY() {
		return this.hasUnsavedRide() ? 100 : 76;
	}

	private void toggleRecording() {
		RideRecorder recorder = this.ctx.rideRecorder;
		if (recorder.isRecording()) {
			recorder.stop();
			this.message = null;
		} else {
			recorder.start();
			this.message = null;
		}
		this.rebuildWidgets();
	}

	private void export() {
		RideRecording ride = this.ctx.rideRecorder.lastRide();
		if (ride == null) {
			return;
		}
		try {
			Path file = RideExport.writeFit(ride, this.ctx.config);
			this.ctx.rideRecorder.markLastRideSaved();
			this.rebuildWidgets();
			this.message = Component.translatable("voxvelo.ride.saved", file.getFileName().toString()).withStyle(ChatFormatting.GREEN);
			if (this.minecraft.player != null) {
				this.minecraft.player.sendSystemMessage(savedMessage(file));
			}
		} catch (IOException e) {
			FitnessRuntime.LOGGER.warn("Could not write the FIT file", e);
			this.message = Component.translatable("voxvelo.ride.save_failed", e.getMessage()).withStyle(ChatFormatting.RED);
		}
	}

	/** A chat line with the file name, clickable like a screenshot link. */
	public static Component savedMessage(Path file) {
		Component name = Component.literal(file.getFileName().toString())
			.withStyle(ChatFormatting.UNDERLINE)
			.withStyle(style -> style.withClickEvent(new ClickEvent.OpenFile(file.toAbsolutePath())));
		return Component.translatable("voxvelo.ride.saved_chat", name);
	}

	private void requestMap() {
		RideRecording ride = this.ctx.rideRecorder.lastRide();
		if (ride != null && RideExport.requestMap(ride)) {
			this.message = Component.translatable("voxvelo.ride.map_requested");
		} else {
			this.message = Component.translatable("voxvelo.ride.map_unavailable").withStyle(ChatFormatting.RED);
		}
	}

	private void openFolder() {
		Path directory = RideExport.ridesDirectory();
		try {
			Files.createDirectories(directory);
			Blaze3D.openPath(directory);
		} catch (IOException | RuntimeException e) {
			FitnessRuntime.LOGGER.warn("Could not open {}", directory, e);
		}
	}

	@Override
	public void tick() {
		// The button reflects stops that did not come from this screen (the key, a full recording).
		RideRecorder recorder = this.ctx.rideRecorder;
		if (!recorder.isRecording() && this.shown != recorder.lastRide()) {
			this.rebuildWidgets();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int cx = this.width / 2;
		graphics.centeredText(this.font, this.title, cx, 20, 0xFFFFFFFF);
		RideRecorder recorder = this.ctx.rideRecorder;
		Component status;
		if (recorder.isRecording()) {
			status = Component.translatable("voxvelo.ride.recording", formatDuration(recorder.elapsedMillis() / 1000.0),
				formatKm(recorder.distanceM())).withStyle(ChatFormatting.RED);
		} else if (this.message != null) {
			status = this.message;
		} else if (this.shown == null) {
			status = Component.translatable("voxvelo.ride.idle");
		} else if (this.shown.isEmpty()) {
			status = Component.translatable("voxvelo.ride.empty");
		} else {
			status = Component.translatable(this.ctx.rideRecorder.lastRideSaved() ? "voxvelo.ride.finished_saved" : "voxvelo.ride.finished");
		}
		graphics.centeredText(this.font, status, cx, 34, 0xFFC0C0C0);
		if (this.hasUnsavedRide()) {
			graphics.centeredText(this.font, Component.translatable("voxvelo.ride.unsaved_warning"), cx, 76, 0xFFFFAA00);
			graphics.centeredText(this.font, Component.translatable("voxvelo.ride.unsaved_export"), cx, 88, 0xFFFFAA00);
		}

		int left = cx - WIDTH / 2;
		int y = this.statsY();
		for (Component[] row : this.stats) {
			if (y + ROW > this.buttonsY()) {
				break;
			}
			this.stat(graphics, left, y, row[0], row[1]);
			if (row.length > 2) {
				this.stat(graphics, left + WIDTH / 2 + 5, y, row[2], row[3]);
			}
			y += ROW;
		}
	}

	private void stat(GuiGraphicsExtractor graphics, int x, int y, Component label, Component value) {
		int column = WIDTH / 2 - 5;
		graphics.text(this.font, label, x, y, 0xFFA0A0A0);
		graphics.text(this.font, value, x + column - this.font.width(value), y, 0xFFFFFFFF);
	}

	private static List<Component[]> statRows(RideSummary s) {
		List<Component[]> rows = new ArrayList<>();
		rows.add(row("distance", Component.translatable("voxvelo.ride.unit_km", formatKm(s.distanceM())),
			"timer", Component.literal(formatDuration(s.timerSeconds()))));
		rows.add(row("elapsed", Component.literal(formatDuration(s.elapsedSeconds())),
			"moving", Component.literal(formatDuration(s.movingSeconds()))));
		rows.add(row("avg_speed", kmh(s.avgSpeedMs()), "max_speed", kmh(s.maxSpeedMs())));
		rows.add(row("avg_power", watts(s.avgPowerWatts()), "max_power", watts(s.avgPowerWatts() >= 0 ? s.maxPowerWatts() : -1)));
		rows.add(row("np", watts(s.normalizedPower()),
			"energy", Component.translatable("voxvelo.ride.unit_energy", Math.round(s.workKj()), s.calories())));
		rows.add(row("avg_cadence", rpm(s.avgCadenceRpm()), "max_cadence", rpm(s.maxCadenceRpm())));
		rows.add(row("avg_hr", bpm(s.avgHeartRate()), "max_hr", bpm(s.maxHeartRate())));
		rows.add(row("ascent", Component.translatable("voxvelo.ride.unit_m", Math.round(s.ascentM())),
			"descent", Component.translatable("voxvelo.ride.unit_m", Math.round(s.descentM()))));
		return rows;
	}

	private static Component[] row(String leftKey, Component leftValue, String rightKey, Component rightValue) {
		return new Component[] {
			Component.translatable("voxvelo.ride.stat." + leftKey), leftValue,
			Component.translatable("voxvelo.ride.stat." + rightKey), rightValue
		};
	}

	private static Component kmh(double ms) {
		return Component.translatable("voxvelo.hud.speed", String.format(Locale.ROOT, "%.1f", ms * 3.6));
	}

	private static Component watts(int value) {
		return value >= 0 ? Component.translatable("voxvelo.hud.power", value) : Component.literal("--");
	}

	private static Component rpm(int value) {
		return value >= 0 ? Component.translatable("voxvelo.hud.cadence", value) : Component.literal("--");
	}

	private static Component bpm(int value) {
		return value > 0 ? Component.translatable("voxvelo.hud.heart_value", value) : Component.literal("--");
	}

	public static String formatKm(double metres) {
		return String.format(Locale.ROOT, "%.2f", metres / 1000.0);
	}

	public static String formatDuration(double seconds) {
		long s = Math.max(0, Math.round(seconds));
		return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
	}

	@Override
	public void onClose() {
		this.ctx.config.save();
		this.minecraft.gui.setScreen(this.parent);
	}
}
