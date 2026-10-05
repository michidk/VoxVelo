package dev.michidk.voxelvelo.client.hud;

import dev.michidk.voxelvelo.bike.BikeEntity;
import dev.michidk.voxelvelo.client.ClientContext;
import dev.michidk.voxelvelo.client.network.BikeControlSender;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Small riding HUD in the bottom-right corner: the numbers that matter while pedaling, and which input
 * source drives each control with a green or red dot so mixed setups are obvious at a glance.
 */
public final class BikeHud implements HudElement {
	private static final int MARGIN = 6;
	private static final int PADDING = 4;
	private static final int LINE_HEIGHT = 10;
	private static final int BACKGROUND = 0x70000000;

	private final ClientContext ctx;
	private final BikeControlSender sender;

	public BikeHud(ClientContext ctx, BikeControlSender sender) {
		this.ctx = ctx;
		this.sender = sender;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (!this.ctx.config.hudEnabled || player == null || !(player.getVehicle() instanceof BikeEntity bike) || bike.getRider() != player) {
			return;
		}

		List<Component> lines = new ArrayList<>();
		double power = this.sender.lastComposed().propulsion();
		OptionalDouble measuredCadence = OptionalDouble.empty();
		OptionalDouble measuredGradient = OptionalDouble.empty();
		for (HudExtras extras : this.ctx.hudExtras()) {
			measuredCadence = measuredCadence.isPresent() ? measuredCadence : extras.cadenceRpm();
			measuredGradient = measuredGradient.isPresent() ? measuredGradient : extras.gradient();
		}
		long cadence = measuredCadence.isPresent()
			? Math.round(measuredCadence.getAsDouble())
			: bike.isPedaling() ? Math.round(bike.getCadenceRevsPerSecond() * 60.0F) : 0;
		lines.add(Component.translatable("voxelvelo.hud.main",
			Component.translatable("voxelvelo.hud.power", Math.round(power)).withStyle(ChatFormatting.GOLD),
			Component.translatable("voxelvelo.hud.cadence", cadence).withStyle(ChatFormatting.AQUA),
			Component.translatable("voxelvelo.hud.speed", String.format("%.1f", bike.getSpeed() * 3.6F)).withStyle(ChatFormatting.GREEN)));
		lines.add(Component.translatable("voxelvelo.hud.terrain",
			Component.translatable("voxelvelo.hud.slope", String.format("%+.1f", measuredGradient.orElse(bike.getGradient()) * 100.0))
				.withStyle(ChatFormatting.YELLOW), bike.getGear()));
		for (HudExtras extras : this.ctx.hudExtras()) {
			extras.addLines(lines);
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
