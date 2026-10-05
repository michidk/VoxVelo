package dev.michidk.voxelvelo.client.hud;

import dev.michidk.voxelvelo.bike.BikeEntity;
import dev.michidk.voxelvelo.client.fitness.FitnessContext;
import dev.michidk.voxelvelo.client.ftms.TrainerTelemetry;
import java.util.Arrays;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Scrolling one-minute history of measured heart rate, sampled once per second. */
public final class HeartRateHud implements HudElement {
	private static final int SAMPLE_COUNT = 60;
	private static final int GRAPH_HEIGHT = 36;
	private final FitnessContext ctx;
	private final int[] history = new int[SAMPLE_COUNT];
	private long lastSecond = -1;
	private int nextSample;
	private Object ridingBike;

	public HeartRateHud(FitnessContext ctx) {
		this.ctx = ctx;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !(mc.player.getVehicle() instanceof BikeEntity bike) || bike.getRider() != mc.player) {
			this.reset(null);
			return;
		}
		if (this.ridingBike != bike) {
			this.reset(bike);
		}
		TrainerTelemetry telemetry = this.ctx.ftms.freshTelemetry();
		int bpm = telemetry != null && telemetry.heartRate() != null ? telemetry.heartRate() : 0;
		if (bpm < 30 || bpm > 250) {
			bpm = 0;
		}
		long second = System.nanoTime() / 1_000_000_000L;
		if (second != this.lastSecond) {
			int elapsed = this.lastSecond < 0 ? 1 : (int) Math.min(SAMPLE_COUNT, second - this.lastSecond);
			for (int i = 1; i < elapsed; i++) {
				this.addSample(0);
			}
			this.addSample(bpm);
			this.lastSecond = second;
		}
		if (!this.ctx.config.heartRateHudEnabled) {
			return;
		}

		Component title = Component.translatable("voxelvelo.hud.heart_title");
		Component value = Component.translatable("voxelvelo.hud.heart_value", bpm > 0 ? String.valueOf(bpm) : "--");
		int width = Math.max(158, mc.font.width(title) + mc.font.width(value) + 18);
		int x = graphics.guiWidth() - width - 6;
		graphics.fill(x, 6, x + width, 80, 0x70000000);
		graphics.text(mc.font, title, x + 6, 11, 0xFFFFFFFF);
		graphics.text(mc.font, value, x + width - mc.font.width(value) - 6, 11, bpm > 0 ? 0xFFFF7777 : 0xFFAAAAAA);

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
		graphics.text(mc.font, Component.literal(String.valueOf(high)), x + 5, graphY - 3, 0xFFAAAAAA);
		graphics.text(mc.font, Component.literal(String.valueOf(low)), x + 5, graphY + GRAPH_HEIGHT - 6, 0xFFAAAAAA);
		for (int i = 0; i <= 2; i++) {
			int y = graphY + i * GRAPH_HEIGHT / 2;
			graphics.fill(graphX, y, graphX + graphWidth, y + 1, 0x55444444);
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
						graphics.fill(px, Math.min(y, nextY), px + 1, Math.max(y, nextY) + 1, 0xFFFF7777);
					}
				} else {
					graphics.fill(pointX, pointY, pointX + 1, pointY + 1, 0xFFFF7777);
				}
				previousX = pointX;
				previousY = pointY;
			}
			previous = sample;
		}
		graphics.text(mc.font, Component.translatable("voxelvelo.hud.heart_history"), graphX, 69, 0xFFAAAAAA);
	}

	private void addSample(int bpm) {
		this.history[this.nextSample] = bpm;
		this.nextSample = (this.nextSample + 1) % SAMPLE_COUNT;
	}

	private void reset(Object bike) {
		Arrays.fill(this.history, 0);
		this.nextSample = 0;
		this.lastSecond = -1;
		this.ridingBike = bike;
	}
}
