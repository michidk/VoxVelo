package dev.michidk.voxvelo.fitnesslib.client.hud;


import dev.michidk.voxvelo.fitnesslib.api.VehicleTelemetry;
import dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime;
import dev.michidk.voxvelo.fitnesslib.client.ftms.TrainerTelemetry;
import dev.michidk.voxvelo.fitnesslib.client.ride.RideRecorder;
import dev.michidk.voxvelo.fitnesslib.client.ride.RideScreen;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.minecraft.network.chat.Component;

/**
 * Small riding HUD in the bottom-right corner: power, cadence, speed, the smoothed slope and gear, and a line while a
 * ride is recorded. Cadence comes from the trainer when it has one, otherwise from the bike.
 */
public final class BikeHud implements HudElement {
	private static final int MARGIN = 6;
	private static final int PADDING = 4;
	private static final int LINE_HEIGHT = 10;
	private static final int BACKGROUND = 0x70000000;

	private final FitnessRuntime ctx;

	public BikeHud(FitnessRuntime ctx) {
		this.ctx = ctx;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		VehicleTelemetry bike = this.ctx.telemetry();
		if (!this.ctx.config.hudEnabled || bike == null) {
			return;
		}

		List<Component> lines = new ArrayList<>();
		double power = bike.powerWatts();
		TrainerTelemetry telemetry = this.ctx.ftms.freshTelemetry();
		long cadence = telemetry != null && telemetry.cadenceRpm() != null
			? Math.round(telemetry.cadenceRpm())
			: Math.round(bike.cadenceRpm());
		lines.add(Component.translatable("voxvelo_fitness_lib.hud.main",
			Component.translatable("voxvelo_fitness_lib.hud.power", Math.round(power)).withStyle(ChatFormatting.GOLD),
			Component.translatable("voxvelo_fitness_lib.hud.cadence", cadence).withStyle(ChatFormatting.AQUA),
			Component.translatable("voxvelo_fitness_lib.hud.speed", String.format("%.1f", bike.speedMps() * 3.6F)).withStyle(ChatFormatting.GREEN)));
		Component grade = Component.translatable("voxvelo_fitness_lib.hud.slope", String.format("%+.1f", bike.gradient() * 100.0))
			.withStyle(ChatFormatting.YELLOW);
		lines.add(bike.gearLabel().isBlank() ? grade : Component.translatable("voxvelo_fitness_lib.hud.terrain", grade, bike.gearLabel()));
		RideRecorder recorder = this.ctx.rideRecorder;
		if (recorder.isRecording()) {
			lines.add(Component.translatable("voxvelo_fitness_lib.ride.hud", RideScreen.formatDuration(recorder.elapsedMillis() / 1000.0),
				RideScreen.formatKm(recorder.distanceM())).withStyle(ChatFormatting.RED));
		}

		Font font = mc.font;
		int width = 0;
		for (Component line : lines) {
			width = Math.max(width, font.width(line));
		}
		int boxWidth = width + PADDING * 2;
		int boxHeight = lines.size() * LINE_HEIGHT + PADDING * 2 - 2;
		int x = graphics.guiWidth() - boxWidth - MARGIN;
		int y = graphics.guiHeight() - boxHeight - MARGIN - 24;
		graphics.fill(x, y, x + boxWidth, y + boxHeight, BACKGROUND);
		for (int i = 0; i < lines.size(); i++) {
			graphics.text(font, lines.get(i), x + PADDING, y + PADDING + i * LINE_HEIGHT, 0xFFFFFFFF);
		}
	}
}
