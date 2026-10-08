package dev.michidk.voxvelo.client.hud;

import dev.michidk.voxvelo.bike.BikeEntity;
import dev.michidk.voxvelo.client.fitness.FitnessContext;
import dev.michidk.voxvelo.client.road.Junction;
import dev.michidk.voxvelo.client.road.JunctionNavigator;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * The big left/right question shown while a junction approaches in road-follow mode. The way the bike takes
 * without an answer is outlined, the picked way is filled green, and a bar runs down until the turn starts.
 */
public final class JunctionPrompt implements HudElement {
	private static final int WIDTH = 240;
	private static final int BOX_WIDTH = 112;
	private static final int BOX_HEIGHT = 30;
	private static final int PANEL = 0x90000000;
	private static final int BOX = 0xC0303030;
	private static final int BOX_PICKED = 0xD01F8F2F;
	private static final int BOX_UNAVAILABLE = 0x50000000;
	private static final int OUTLINE_DEFAULT = 0xFFFFD34D;
	private static final int OUTLINE = 0xFFB0B0B0;

	private final FitnessContext ctx;

	public JunctionPrompt(FitnessContext ctx) {
		this.ctx = ctx;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		JunctionNavigator.Prompt prompt = this.ctx.roadFollow.prompt();
		if (!this.ctx.config.junctionHudEnabled || prompt == null || player == null || !(player.getVehicle() instanceof BikeEntity bike) || bike.getRider() != player) {
			return;
		}

		Font font = mc.font;
		int centre = graphics.guiWidth() / 2;
		int left = centre - WIDTH / 2;
		int top = graphics.guiHeight() / 5;
		graphics.fill(left, top, left + WIDTH, top + 92, PANEL);

		this.scaledText(graphics, font, Component.translatable("voxvelo.junction.title"), centre, top + 6, 1.5F, 0xFFFFFFFF);
		int boxTop = top + 26;
		this.box(graphics, font, left + 6, boxTop, Component.translatable("voxvelo.junction.left"), prompt.left(), prompt, Junction.Side.LEFT);
		this.box(graphics, font, left + WIDTH - 6 - BOX_WIDTH, boxTop, Component.translatable("voxvelo.junction.right"), prompt.right(), prompt, Junction.Side.RIGHT);

		int barLeft = left + 6;
		int barWidth = WIDTH - 12;
		int barTop = boxTop + BOX_HEIGHT + 6;
		graphics.fill(barLeft, barTop, barLeft + barWidth, barTop + 4, 0x80000000);
		graphics.fill(barLeft, barTop, barLeft + (int) Math.round(barWidth * prompt.remaining()), barTop + 4, 0xFFFFFFFF);

		Component keys = Component.translatable("voxvelo.junction.hint", mc.options.keyLeft.getTranslatedKeyMessage(), mc.options.keyRight.getTranslatedKeyMessage());
		graphics.centeredText(font, keys, centre, barTop + 10, 0xFFE0E0E0);
		Junction.Side fallback = prompt.chosen() != null ? prompt.chosen() : prompt.defaultSide();
		graphics.centeredText(font, Component.translatable("voxvelo.junction.default", Component.translatable("voxvelo.junction.side." + fallback.name().toLowerCase())), centre, barTop + 21,
			prompt.chosen() != null ? 0xFF7CFF8A : 0xFFFFD34D);
	}

	private void box(GuiGraphicsExtractor graphics, Font font, int x, int y, Component label, boolean available, JunctionNavigator.Prompt prompt, Junction.Side side) {
		boolean picked = prompt.chosen() == side;
		graphics.fill(x, y, x + BOX_WIDTH, y + BOX_HEIGHT, !available ? BOX_UNAVAILABLE : picked ? BOX_PICKED : BOX);
		if (available) {
			int outline = prompt.chosen() == null && prompt.defaultSide() == side ? OUTLINE_DEFAULT : OUTLINE;
			graphics.fill(x, y, x + BOX_WIDTH, y + 1, outline);
			graphics.fill(x, y + BOX_HEIGHT - 1, x + BOX_WIDTH, y + BOX_HEIGHT, outline);
			graphics.fill(x, y, x + 1, y + BOX_HEIGHT, outline);
			graphics.fill(x + BOX_WIDTH - 1, y, x + BOX_WIDTH, y + BOX_HEIGHT, outline);
		}
		this.scaledText(graphics, font, label, x + BOX_WIDTH / 2, y + 8, 2.0F, available ? 0xFFFFFFFF : 0xFF707070);
	}

	private void scaledText(GuiGraphicsExtractor graphics, Font font, Component text, int centreX, int y, float scale, int color) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(centreX, y);
		graphics.pose().scale(scale, scale);
		graphics.centeredText(font, text, 0, 0, color);
		graphics.pose().popMatrix();
	}
}
