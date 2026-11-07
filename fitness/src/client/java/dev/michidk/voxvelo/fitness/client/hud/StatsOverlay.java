package dev.michidk.voxvelo.fitness.client.hud;

import dev.michidk.voxvelo.fitness.client.fitness.FitnessContext;
import dev.michidk.voxvelo.fitness.client.stats.RiderStatsStore;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/**
 * Top-left list of other bike riders with power, speed, cadence and shared heart rate. Toggled with a key or in the
 * settings, and empty (so invisible) when no other players are riding.
 */
public final class StatsOverlay implements HudElement {
	private static final int MARGIN = 6;
	private static final int PADDING = 4;
	private static final int LINE_HEIGHT = 10;
	private static final int BACKGROUND = 0x70000000;

	private final FitnessContext ctx;

	public StatsOverlay(FitnessContext ctx) {
		this.ctx = ctx;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (!this.ctx.config.statsOverlayEnabled || mc.player == null || mc.getConnection() == null) {
			return;
		}

		List<Component> lines = new ArrayList<>();
		for (RiderStatsStore.Stats stats : this.ctx.riderStats.fresh()) {
			if (stats.player().equals(mc.player.getUUID())) {
				continue;
			}
			if (lines.size() >= Math.min(64, (graphics.guiHeight() / 2 - PADDING * 2) / LINE_HEIGHT)) {
				continue;
			}
			PlayerInfo info = mc.getConnection().getPlayerInfo(stats.player());
			String name = info == null ? "?" : info.getProfile().name();
			String watts = stats.watts() >= 0 ? stats.watts() + " W" : "-- W";
			String pulse = stats.heartRate() > 0 ? "♥ " + stats.heartRate() : "♥ --";
			lines.add(Component.translatable("voxvelo_fitness_lib.stats.line", name,
				Component.literal(watts).withStyle(ChatFormatting.GOLD),
				Component.translatable("voxvelo_fitness_lib.hud.speed", String.format("%.1f", stats.speedKmh())).withStyle(ChatFormatting.GREEN),
				Component.translatable("voxvelo_fitness_lib.hud.cadence", stats.cadence()).withStyle(ChatFormatting.AQUA),
				Component.literal(pulse).withStyle(ChatFormatting.RED),
				Component.translatable("voxvelo_fitness_lib.hud.gear", stats.gear()).withStyle(ChatFormatting.LIGHT_PURPLE)));
		}
		if (lines.isEmpty()) {
			return;
		}

		Font font = mc.font;
		int width = 0;
		for (Component line : lines) {
			width = Math.max(width, font.width(line));
		}
		int x = MARGIN;
		int y = MARGIN;
		graphics.fill(x, y, x + width + PADDING * 2, y + lines.size() * LINE_HEIGHT + PADDING * 2 - 2, BACKGROUND);
		for (int i = 0; i < lines.size(); i++) {
			graphics.text(font, lines.get(i), x + PADDING, y + PADDING + i * LINE_HEIGHT, 0xFFFFFFFF);
		}
	}
}
