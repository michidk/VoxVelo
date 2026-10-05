package dev.michidk.voxelvelo.client.fitness;

import dev.michidk.voxelvelo.client.ui.Tips;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The rider stats overlay, what this player shares with the others, and the input-source lines of the HUD. */
public class StatsScreen extends Screen {
	private static final int WIDTH = 310;

	private final Screen parent;
	private final FitnessContext ctx = FitnessContext.get();

	public StatsScreen(Screen parent) {
		super(Component.translatable("voxelvelo.stats.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - WIDTH / 2;
		int y = 46;
		this.addRenderableWidget(CycleButton.onOffBuilder(this.ctx.config.hudShowSources)
			.withTooltip(value -> Tips.of("voxelvelo.rider.hud_sources"))
			.create(left, y, WIDTH, 20, Component.translatable("voxelvelo.rider.hud_sources"), (button, value) -> this.ctx.config.hudShowSources = value));
		y += 32;
		this.addRenderableWidget(CycleButton.onOffBuilder(this.ctx.config.statsOverlayEnabled)
			.withTooltip(value -> Tips.of("voxelvelo.rider.stats"))
			.create(left, y, WIDTH, 20, Component.translatable("voxelvelo.rider.stats"), (button, value) -> this.ctx.config.statsOverlayEnabled = value));
		y += 24;
		int half = (WIDTH - 10) / 2;
		this.addRenderableWidget(CycleButton.onOffBuilder(this.ctx.config.shareWatts)
			.withTooltip(value -> Tips.of("voxelvelo.rider.share_watts"))
			.create(left, y, half, 20, Component.translatable("voxelvelo.rider.share_watts"), (button, value) -> this.ctx.config.shareWatts = value));
		this.addRenderableWidget(CycleButton.onOffBuilder(this.ctx.config.shareHeartRate)
			.withTooltip(value -> Tips.of("voxelvelo.rider.share_hr"))
			.create(left + half + 10, y, half, 20, Component.translatable("voxelvelo.rider.share_hr"), (button, value) -> this.ctx.config.shareHeartRate = value));

		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
			.bounds(left, this.height - 30, WIDTH, 20).build());
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
