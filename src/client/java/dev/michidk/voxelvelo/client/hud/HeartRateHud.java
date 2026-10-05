package dev.michidk.voxelvelo.client.hud;

import dev.michidk.voxelvelo.bike.BikeEntity;
import dev.michidk.voxelvelo.client.fitness.FitnessContext;
import dev.michidk.voxelvelo.client.ftms.TrainerTelemetry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Personal pulse display; the heart flashes once per reported heartbeat. */
public final class HeartRateHud implements HudElement {
	private final FitnessContext ctx;

	public HeartRateHud(FitnessContext ctx) {
		this.ctx = ctx;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (!this.ctx.core.config.hudEnabled || mc.player == null
			|| !(mc.player.getVehicle() instanceof BikeEntity bike) || bike.getRider() != mc.player) {
			return;
		}
		TrainerTelemetry telemetry = this.ctx.ftms.freshTelemetry();
		int bpm = telemetry != null && telemetry.heartRate() != null ? telemetry.heartRate() : 0;
		Component title = Component.translatable("voxelvelo.hud.heart_title");
		Component value = Component.translatable("voxelvelo.hud.heart_value", bpm > 0 ? String.valueOf(bpm) : "--");
		int width = Math.max(mc.font.width(title), mc.font.width(value) + 16) + 12;
		int x = graphics.guiWidth() - width - 6;
		graphics.fill(x, 6, x + width, 38, 0x70000000);
		graphics.text(mc.font, title, x + 6, 11, 0xFFFFFFFF);
		double phase = bpm > 0 ? (System.nanoTime() / 1_000_000_000.0 * bpm / 60.0) % 1.0 : 1.0;
		int heartColor = bpm <= 0 ? 0xFF888888 : phase < 0.2 ? 0xFFFF5555 : 0xFFAA3333;
		graphics.text(mc.font, Component.literal("♥"), x + 6, 25, heartColor);
		graphics.text(mc.font, value, x + 22, 25, bpm > 0 ? 0xFFFF7777 : 0xFFAAAAAA);
	}
}
