package dev.michidk.voxvelo.client.fitness;

import dev.michidk.voxvelo.client.ui.Tips;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The riding HUD parts, the rider stats overlay, and what this player shares with the others. */
public class StatsScreen extends Screen {
	private static final int WIDTH = 310;

	private final Screen parent;
	private final FitnessContext ctx = FitnessContext.get();

	public StatsScreen(Screen parent) {
		super(Component.translatable("voxvelo.stats.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - WIDTH / 2;
		int y = 46;
		int half = (WIDTH - 10) / 2;
		int right = left + half + 10;
		this.toggle(left, y, half, "voxvelo.rider.hud", this.ctx.config.hudEnabled,
			value -> this.ctx.config.hudEnabled = value);
		this.toggle(right, y, half, "voxvelo.rider.heart_graph", this.ctx.config.heartRateHudEnabled,
			value -> this.ctx.config.heartRateHudEnabled = value);
		y += 24;
		this.toggle(left, y, half, "voxvelo.rider.stats", this.ctx.config.statsOverlayEnabled,
			value -> this.ctx.config.statsOverlayEnabled = value);
		this.toggle(right, y, half, "voxvelo.rider.junction_hud", this.ctx.config.junctionHudEnabled,
			value -> this.ctx.config.junctionHudEnabled = value);
		y += 32;
		this.toggle(left, y, half, "voxvelo.rider.share_watts", this.ctx.config.shareWatts,
			value -> this.ctx.config.shareWatts = value);
		this.toggle(right, y, half, "voxvelo.rider.share_hr", this.ctx.config.shareHeartRate,
			value -> this.ctx.config.shareHeartRate = value);

		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
			.bounds(left, this.height - 30, WIDTH, 20).build());
	}

	private void toggle(int x, int y, int width, String key, boolean enabled, java.util.function.Consumer<Boolean> change) {
		this.addRenderableWidget(CycleButton.onOffBuilder(enabled)
			.withTooltip(value -> Tips.of(key))
			.create(x, y, width, 20, Component.translatable(key), (button, value) -> change.accept(value)));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		this.ctx.config.save();
		this.minecraft.gui.setScreen(this.parent);
	}
}
