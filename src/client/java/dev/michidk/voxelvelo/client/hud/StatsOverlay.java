package dev.michidk.voxelvelo.client.hud;

import dev.michidk.voxelvelo.client.ClientContext;
import dev.michidk.voxelvelo.client.stats.RiderStatsStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/**
 * Top-left list of the other riders and what they share: watts and heart rate. Toggled with a key or in the
 * settings, and empty (so invisible) when nobody is sharing.
 */
public final class StatsOverlay implements HudElement {
	private static final int MAX_LINES = 10;
	private static final int MARGIN = 6;
	private static final int PADDING = 4;
	private static final int LINE_HEIGHT = 10;
	private static final int BACKGROUND = 0x70000000;

	private final ClientContext ctx;

	public StatsOverlay(ClientContext ctx) {
		this.ctx = ctx;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (!this.ctx.config.statsOverlayEnabled || mc.player == null || mc.getConnection() == null) {
			return;
		}
		UUID self = mc.player.getUUID();
		List<Component> lines = new ArrayList<>();
		for (RiderStatsStore.Stats stats : this.ctx.riderStats.fresh()) {
			if (stats.player().equals(self) || lines.size() >= MAX_LINES) {
				continue;
			}
			PlayerInfo info = mc.getConnection().getPlayerInfo(stats.player());
			String name = info == null ? "?" : info.getProfile().name();
			String watts = stats.watts() >= 0 ? stats.watts() + " W" : "-- W";
			String pulse = stats.heartRate() > 0 ? "♥ " + stats.heartRate() : "♥ --";
			lines.add(Component.translatable("voxelvelo.stats.line", name, watts, pulse));
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
