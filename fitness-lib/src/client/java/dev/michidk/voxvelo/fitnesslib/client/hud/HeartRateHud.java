package dev.michidk.voxvelo.fitnesslib.client.hud;

import dev.michidk.voxvelo.fitnesslib.api.VehicleTelemetry;
import dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime;
import dev.michidk.voxvelo.fitnesslib.client.fitness.HeartRatePreference;
import dev.michidk.voxvelo.fitnesslib.client.ftms.TrainerTelemetry;
import java.util.Arrays;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Scrolling one-minute history of measured heart rate, sampled once per second, over the grade ridden at the same
 * time: the grade is a faint area around the middle gridline, uphill above it and downhill below it.
 */
public final class HeartRateHud implements HudElement {
	private static final int SAMPLE_COUNT = 60;
	private static final int GRAPH_HEIGHT = 36;
	/** The grade profile fills half the graph at this grade, or at the steepest grade shown when that is steeper. */
	private static final double MIN_GRADE_SCALE = 0.05;
	private static final int GRADE_AREA = 0x204C6470;
private static final int HEART_RED = 0xFFFF5266;
	private final FitnessRuntime ctx;
	private final int[] history = new int[SAMPLE_COUNT];
	private final double[] gradeHistory = new double[SAMPLE_COUNT];
	private long lastSecond = -1;
	private int nextSample;
	private Object ridingBike;

	public HeartRateHud(FitnessRuntime ctx) {
		this.ctx = ctx;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		VehicleTelemetry bike = this.ctx.telemetry();
		if (bike == null) {
			this.reset(null);
			return;
		}
		if (this.ridingBike != this.ctx.vehicleIdentity()) {
			this.reset(this.ctx.vehicleIdentity());
		}
		TrainerTelemetry telemetry = this.ctx.ftms.freshTelemetry();
		Integer measured = HeartRatePreference.bpm(this.ctx.heartRate.freshReading(), telemetry);
		int bpm = measured == null ? 0 : measured;
		double grade = bike.gradient();
		long second = System.nanoTime() / 1_000_000_000L;
		if (second != this.lastSecond) {
			int elapsed = this.lastSecond < 0 ? 1 : (int) Math.min(SAMPLE_COUNT, second - this.lastSecond);
			for (int i = 1; i < elapsed; i++) {
				this.addSample(0, 0.0);
			}
			this.addSample(bpm, grade);
			this.lastSecond = second;
		}
		// Shown while a heart rate sensor is connected, or the trainer is and relays heart rate.
		boolean trainerRelays = this.ctx.ftms.isConnected() && telemetry != null && telemetry.heartRate() != null;
		if (!this.ctx.config.heartRateHudEnabled || !(this.ctx.heartRate.isConnected() || trainerRelays)) {
			return;
		}

		Component title = Component.translatable("voxvelo_fitness_lib.hud.heart_title");
		Component value = Component.translatable("voxvelo_fitness_lib.hud.heart_value", bpm > 0 ? String.valueOf(bpm) : "--");
		Component history = Component.translatable("voxvelo_fitness_lib.hud.heart_history");
		Component gradeText = Component.translatable("voxvelo_fitness_lib.hud.heart_grade",
			Component.translatable("voxvelo_fitness_lib.hud.slope", String.format("%+.1f", grade * 100.0)));
		int width = Math.max(158, Math.max(mc.font.width(title) + mc.font.width(value) + 32, mc.font.width(history) + mc.font.width(gradeText) + 44));
		int x = graphics.guiWidth() - width - 6;
		graphics.fill(x, 6, x + width, 80, 0xCE10151C);
		graphics.fill(x, 6, x + 2, 80, HEART_RED);
		graphics.fill(x + 2, 6, x + width, 7, 0x607A3540);
		drawHeart(graphics, x + 7, 12);
		graphics.text(mc.font, title, x + 19, 11, HEART_RED);
		graphics.text(mc.font, value, x + width - mc.font.width(value) - 6, 11, bpm > 0 ? HEART_RED : 0xFFB88088);

		int min = 250;
		int max = 30;
		boolean hasSamples = false;
		for (int sample : this.history) {
			if (sample > 0) {
				min = Math.min(min, sample);
				max = Math.max(max, sample);
				hasSamples = true;
			}
		}
		int low = hasSamples ? Math.max(30, (min / 10) * 10 - 10) : 60;
		int high = hasSamples ? Math.min(250, ((max + 9) / 10) * 10 + 10) : 180;
		int graphX = x + 30;
		int graphY = 27;
		int graphWidth = width - 36;
		graphics.text(mc.font, Component.literal(String.valueOf(high)), x + 5, graphY - 3, 0xFF94A1AD);
		graphics.text(mc.font, Component.literal(String.valueOf(low)), x + 5, graphY + GRAPH_HEIGHT - 6, 0xFF94A1AD);
		for (int i = 0; i <= 2; i++) {
			int y = graphY + i * GRAPH_HEIGHT / 2;
			graphics.fill(graphX, y, graphX + graphWidth, y + 1, 0x304B5966);
		}
		double gradeScale = MIN_GRADE_SCALE;
		for (double sample : this.gradeHistory) {
			gradeScale = Math.max(gradeScale, Math.abs(sample));
		}
		int middleY = graphY + GRAPH_HEIGHT / 2;
		for (int i = 0; i < SAMPLE_COUNT - 1; i++) {
			double sample = this.gradeHistory[(this.nextSample + i + 1) % SAMPLE_COUNT];
			int fromX = graphX + i * (graphWidth - 1) / (SAMPLE_COUNT - 1);
			int toX = graphX + (i + 1) * (graphWidth - 1) / (SAMPLE_COUNT - 1);
			int height = (int) Math.round(sample / gradeScale * (GRAPH_HEIGHT / 2));
			graphics.fill(fromX, Math.min(middleY, middleY - height), toX + 1, Math.max(middleY, middleY - height) + 1, GRADE_AREA);
		}
		int previous = 0;
		int previousX = 0;
		int previousY = 0;
		for (int i = 0; i < SAMPLE_COUNT; i++) {
			int sample = this.history[(this.nextSample + i) % SAMPLE_COUNT];
			int pointX = graphX + i * (graphWidth - 1) / (SAMPLE_COUNT - 1);
			if (sample > 0) {
				int pointY = graphY + GRAPH_HEIGHT - (sample - low) * GRAPH_HEIGHT / (high - low);
				if (previous > 0) {
					for (int px = previousX; px <= pointX; px++) {
						int y = previousY + (pointY - previousY) * (px - previousX) / (pointX - previousX);
						int nextY = previousY + (pointY - previousY) * Math.min(pointX - previousX, px - previousX + 1) / (pointX - previousX);
						graphics.fill(px, y, px + 1, graphY + GRAPH_HEIGHT, 0x28FF5266);
						graphics.fill(px, Math.min(y, nextY), px + 1, Math.min(graphY + GRAPH_HEIGHT + 1, Math.max(y, nextY) + 2), HEART_RED);
					}
				} else {
					graphics.fill(pointX, pointY, pointX + 2, pointY + 2, HEART_RED);
				}
				previousX = pointX;
				previousY = pointY;
			}
			previous = sample;
		}
		graphics.text(mc.font, history, graphX, 69, 0xFF94A1AD);
		graphics.text(mc.font, gradeText, x + width - mc.font.width(gradeText) - 6, 69, 0xFF94A1AD);
	}

	/** Small pixel heart stays crisp at every Minecraft GUI scale. */
	private static void drawHeart(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.fill(x + 1, y, x + 4, y + 2, HEART_RED);
		graphics.fill(x + 6, y, x + 9, y + 2, HEART_RED);
		graphics.fill(x, y + 2, x + 10, y + 4, HEART_RED);
		graphics.fill(x + 1, y + 4, x + 9, y + 5, HEART_RED);
		graphics.fill(x + 2, y + 5, x + 8, y + 6, HEART_RED);
		graphics.fill(x + 3, y + 6, x + 7, y + 7, HEART_RED);
		graphics.fill(x + 4, y + 7, x + 6, y + 8, HEART_RED);
	}

	private void addSample(int bpm, double grade) {
		this.history[this.nextSample] = bpm;
		this.gradeHistory[this.nextSample] = Double.isFinite(grade) ? grade : 0.0;
		this.nextSample = (this.nextSample + 1) % SAMPLE_COUNT;
	}

	private void reset(Object bike) {
		Arrays.fill(this.history, 0);
		Arrays.fill(this.gradeHistory, 0.0);
		this.nextSample = 0;
		this.lastSecond = -1;
		this.ridingBike = bike;
	}
}
