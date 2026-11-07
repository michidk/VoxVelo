package dev.michidk.voxvelo.fitnesslib.client.hud;

import dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Shows received steering and the vehicle's actual wheel angle independently. */
public final class SteeringHud implements HudElement {
	private final FitnessRuntime ctx;

	public SteeringHud(FitnessRuntime ctx) { this.ctx = ctx; }

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (!this.ctx.config.steeringHudEnabled || mc.level == null || mc.player == null) return;
		Double input = this.ctx.obc.isConnected() ? this.ctx.obc.controls().steeringAngleDegrees() : null;
		Double wheel = this.ctx.vehicleSteeringAngleDegrees();
		Component inputText = Component.translatable("voxvelo_fitness_lib.hud.steering_input", angle(input));
		Component wheelText = Component.translatable("voxvelo_fitness_lib.hud.steering_wheel", angle(wheel));
		int width = Math.max(124, Math.max(mc.font.width(inputText), mc.font.width(wheelText)) + 12);
		int x = (graphics.guiWidth() - width) / 2;
		int y = graphics.guiHeight() - 100;
		graphics.fill(x, y, x + width, y + 40, 0x90000000);
		graphics.text(mc.font, inputText, x + 6, y + 4, 0xFF55FFFF);
		graphics.text(mc.font, wheelText, x + 6, y + 14, 0xFFFFD060);
		int left = x + 8, right = x + width - 8, center = (left + right) / 2;
		graphics.fill(left, y + 31, right, y + 32, 0xFF707070);
		graphics.fill(center, y + 27, center + 1, y + 37, 0xFFAAAAAA);
		marker(graphics, input, left, right, y + 27, 0xFF55FFFF);
		marker(graphics, wheel, left, right, y + 33, 0xFFFFD060);
	}

	private static String angle(Double value) {
		return value == null || !Double.isFinite(value) ? "—" : String.format(Locale.ROOT, "%+.1f°", value);
	}

	private static void marker(GuiGraphicsExtractor graphics, Double angle, int left, int right, int y, int color) {
		if (angle == null || !Double.isFinite(angle)) return;
		int x = (int) Math.round((left + right) / 2.0 + Math.clamp(angle / 63.0, -1, 1) * (right - left) / 2.0);
		graphics.fill(x - 1, y, x + 2, y + 4, color);
	}
}
