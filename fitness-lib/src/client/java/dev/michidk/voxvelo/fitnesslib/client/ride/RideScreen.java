package dev.michidk.voxvelo.fitnesslib.client.ride;

import com.mojang.blaze3d.Blaze3D;
import dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime;
import dev.michidk.voxvelo.fitnesslib.client.ui.SettingsList;
import dev.michidk.voxvelo.fitnesslib.client.ui.SettingsScreen;
import dev.michidk.voxvelo.fitnesslib.client.ui.Tips;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
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
public class RideScreen extends SettingsScreen {
	private static final int WIDTH = SettingsList.WIDTH;
	private static final int HALF = SettingsList.HALF;
	private static final int WARNING = 0xFFFFAA00;

	private final FitnessRuntime ctx = FitnessRuntime.get();
	private @Nullable Component message;
	private @Nullable RideRecording shown;
	private List<Component[]> stats = List.of();

	public RideScreen(@Nullable Screen parent) {
		super(parent, Component.translatable("voxvelo_fitness_lib.ride.title"));
	}

	@Override
	protected void addOptions() {
		RideRecorder recorder = this.ctx.rideRecorder;
		RideRecording ride = recorder.isRecording() ? null : recorder.lastRide();
		if (ride != this.shown) {
			this.shown = ride;
			this.stats = ride == null ? List.of() : statRows(ride.summary());
		}

		this.rows.addText(this::status, STATUS);
		Component toggle = Component.translatable(recorder.isRecording() ? "voxvelo_fitness_lib.ride.stop" : "voxvelo_fitness_lib.ride.start");
		this.rows.addRow(Button.builder(toggle, button -> this.toggleRecording())
			.tooltip(Tips.of(recorder.isRecording() ? "voxvelo_fitness_lib.ride.stop" : "voxvelo_fitness_lib.ride.start"))
			.bounds(0, 0, WIDTH, 20).build());

		if (this.hasUnsavedRide()) {
			this.rows.addText(() -> Component.translatable("voxvelo_fitness_lib.ride.unsaved_warning"), WARNING);
			this.rows.addText(() -> Component.translatable("voxvelo_fitness_lib.ride.unsaved_export"), WARNING);
		}
		for (Component[] row : this.stats) {
			this.rows.addText((graphics, font, left, top) -> {
				stat(graphics, font, left, top, row[0], row[1]);
				if (row.length > 2) {
					stat(graphics, font, left + WIDTH / 2 + 5, top, row[2], row[3]);
				}
			});
		}

		Button export = Button.builder(Component.translatable("voxvelo_fitness_lib.ride.export"), button -> this.export())
			.tooltip(Tips.of("voxvelo_fitness_lib.ride.export")).bounds(0, 0, HALF, 20).build();
		Button map = Button.builder(Component.translatable("voxvelo_fitness_lib.ride.map"), button -> this.requestMap())
			.tooltip(Tips.of("voxvelo_fitness_lib.ride.map")).bounds(WIDTH - HALF, 0, HALF, 20).build();
		export.active = ride != null && !ride.isEmpty();
		map.active = ride != null && !ride.isEmpty() && RideExport.canRequestMap();
		this.rows.addRow(export, map);
		this.rows.addRow(CycleButton.onOffBuilder(this.ctx.config.rideFitPosition)
				.withTooltip(value -> Tips.of("voxvelo_fitness_lib.ride.fit_position"))
				.create(0, 0, HALF, 20, Component.translatable("voxvelo_fitness_lib.ride.fit_position"),
					(button, value) -> this.ctx.config.rideFitPosition = value),
			Button.builder(Component.translatable("voxvelo_fitness_lib.ride.open_folder"), button -> this.openFolder())
				.tooltip(Tips.of("voxvelo_fitness_lib.ride.open_folder")).bounds(WIDTH - HALF, 0, HALF, 20).build());
	}

	private boolean hasUnsavedRide() {
		return !this.ctx.rideRecorder.isRecording() && this.shown != null && !this.shown.isEmpty()
			&& !this.ctx.rideRecorder.lastRideSaved();
	}

	/** The running recording, the outcome of the last action, or what the shown ride is. */
	private Component status() {
		RideRecorder recorder = this.ctx.rideRecorder;
		if (recorder.isRecording()) {
			return Component.translatable("voxvelo_fitness_lib.ride.recording", formatDuration(recorder.elapsedMillis() / 1000.0),
				formatKm(recorder.distanceM())).withStyle(ChatFormatting.RED);
		} else if (this.message != null) {
			return this.message;
		} else if (this.shown == null) {
			return Component.translatable("voxvelo_fitness_lib.ride.idle");
		} else if (this.shown.isEmpty()) {
			return Component.translatable("voxvelo_fitness_lib.ride.empty");
		}
		return Component.translatable(recorder.lastRideSaved() ? "voxvelo_fitness_lib.ride.finished_saved" : "voxvelo_fitness_lib.ride.finished");
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
			this.message = Component.translatable("voxvelo_fitness_lib.ride.saved", file.getFileName().toString()).withStyle(ChatFormatting.GREEN);
			if (this.minecraft.player != null) {
				this.minecraft.player.sendSystemMessage(savedMessage(file));
			}
		} catch (IOException e) {
			FitnessRuntime.LOGGER.warn("Could not write the FIT file", e);
			this.message = Component.translatable("voxvelo_fitness_lib.ride.save_failed", e.getMessage()).withStyle(ChatFormatting.RED);
		}
	}

	/** A chat line with the file name, clickable like a screenshot link. */
	public static Component savedMessage(Path file) {
		Component name = Component.literal(file.getFileName().toString())
			.withStyle(ChatFormatting.UNDERLINE)
			.withStyle(style -> style.withClickEvent(new ClickEvent.OpenFile(file.toAbsolutePath())));
		return Component.translatable("voxvelo_fitness_lib.ride.saved_chat", name);
	}

	private void requestMap() {
		RideRecording ride = this.ctx.rideRecorder.lastRide();
		if (ride != null && RideExport.requestMap(ride)) {
			this.message = Component.translatable("voxvelo_fitness_lib.ride.map_requested");
		} else {
			this.message = Component.translatable("voxvelo_fitness_lib.ride.map_unavailable").withStyle(ChatFormatting.RED);
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

	private static void stat(GuiGraphicsExtractor graphics, Font font, int x, int y, Component label, Component value) {
		int column = WIDTH / 2 - 5;
		graphics.text(font, label, x, y, 0xFFA0A0A0);
		graphics.text(font, value, x + column - font.width(value), y, 0xFFFFFFFF);
	}

	private static List<Component[]> statRows(RideSummary s) {
		List<Component[]> rows = new ArrayList<>();
		rows.add(row("distance", Component.translatable("voxvelo_fitness_lib.ride.unit_km", formatKm(s.distanceM())),
			"timer", Component.literal(formatDuration(s.timerSeconds()))));
		rows.add(row("elapsed", Component.literal(formatDuration(s.elapsedSeconds())),
			"moving", Component.literal(formatDuration(s.movingSeconds()))));
		rows.add(row("avg_speed", kmh(s.avgSpeedMs()), "max_speed", kmh(s.maxSpeedMs())));
		rows.add(row("avg_power", watts(s.avgPowerWatts()), "max_power", watts(s.avgPowerWatts() >= 0 ? s.maxPowerWatts() : -1)));
		rows.add(row("np", watts(s.normalizedPower()),
			"energy", Component.translatable("voxvelo_fitness_lib.ride.unit_energy", Math.round(s.workKj()), s.calories())));
		rows.add(row("avg_cadence", rpm(s.avgCadenceRpm()), "max_cadence", rpm(s.maxCadenceRpm())));
		rows.add(row("avg_hr", bpm(s.avgHeartRate()), "max_hr", bpm(s.maxHeartRate())));
		rows.add(row("ascent", Component.translatable("voxvelo_fitness_lib.ride.unit_m", Math.round(s.ascentM())),
			"descent", Component.translatable("voxvelo_fitness_lib.ride.unit_m", Math.round(s.descentM()))));
		return rows;
	}

	private static Component[] row(String leftKey, Component leftValue, String rightKey, Component rightValue) {
		return new Component[] {
			Component.translatable("voxvelo_fitness_lib.ride.stat." + leftKey), leftValue,
			Component.translatable("voxvelo_fitness_lib.ride.stat." + rightKey), rightValue
		};
	}

	private static Component kmh(double ms) {
		return Component.translatable("voxvelo_fitness_lib.hud.speed", String.format(Locale.ROOT, "%.1f", ms * 3.6));
	}

	private static Component watts(int value) {
		return value >= 0 ? Component.translatable("voxvelo_fitness_lib.hud.power", value) : Component.literal("--");
	}

	private static Component rpm(int value) {
		return value >= 0 ? Component.translatable("voxvelo_fitness_lib.hud.cadence", value) : Component.literal("--");
	}

	private static Component bpm(int value) {
		return value > 0 ? Component.translatable("voxvelo_fitness_lib.hud.heart_value", value) : Component.literal("--");
	}

	public static String formatKm(double metres) {
		return String.format(Locale.ROOT, "%.2f", metres / 1000.0);
	}

	public static String formatDuration(double seconds) {
		long s = Math.max(0, Math.round(seconds));
		return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
	}

	@Override
	protected void save() {
		this.ctx.config.save();
	}
}
