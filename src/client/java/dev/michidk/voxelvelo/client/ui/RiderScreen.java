package dev.michidk.voxelvelo.client.ui;

import dev.michidk.voxelvelo.client.ClientContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Rider mass, personal speed limit and the riding HUD. Mass and limit are sent to the server (and clamped there). */
public class RiderScreen extends Screen {
	private static final int WIDTH = 310;
	private static final double MIN_MASS = 40.0;
	private static final double MAX_MASS = 150.0;
	private static final double MIN_LIMIT = 5.0;
	private static final double MAX_LIMIT = 100.0;
	/** The first slice of the limit slider means "no limit". */
	private static final double OFF_ZONE = 0.05;

	private final Screen parent;
	private final ClientContext ctx = ClientContext.get();

	public RiderScreen(Screen parent) {
		super(Component.translatable("voxelvelo.rider.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - WIDTH / 2;
		int y = 46;
		this.addRenderableWidget(new MassSlider(left, y));
		y += 24;
		this.addRenderableWidget(new LimitSlider(left, y));
		y += 28;
		this.addRenderableWidget(CycleButton.onOffBuilder(this.ctx.config.hudEnabled)
			.create(left, y, WIDTH, 20, Component.translatable("voxelvelo.rider.hud"), (button, value) -> this.ctx.config.hudEnabled = value));
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
			.bounds(left, this.height - 30, WIDTH, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
		graphics.centeredText(this.font, Component.translatable("voxelvelo.rider.note"), this.width / 2, 33, 0xFF909090);
	}

	@Override
	public void onClose() {
		this.ctx.config.save();
		this.ctx.sendRiderSettings();
		this.minecraft.gui.setScreen(this.parent);
	}

	private final class MassSlider extends AbstractSliderButton {
		MassSlider(int x, int y) {
			super(x, y, WIDTH, 20, Component.empty(), (RiderScreen.this.ctx.config.riderMassKg - MIN_MASS) / (MAX_MASS - MIN_MASS));
			this.updateMessage();
		}

		private double mass() {
			return Math.round(MIN_MASS + this.value * (MAX_MASS - MIN_MASS));
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.translatable("voxelvelo.rider.mass", Math.round(this.mass())));
		}

		@Override
		protected void applyValue() {
			RiderScreen.this.ctx.config.riderMassKg = this.mass();
		}
	}

	private final class LimitSlider extends AbstractSliderButton {
		LimitSlider(int x, int y) {
			super(x, y, WIDTH, 20, Component.empty(), initial(RiderScreen.this.ctx.config.speedLimitKmh));
			this.updateMessage();
		}

		private static double initial(double limit) {
			return limit <= 0 ? 0.0 : OFF_ZONE + (limit - MIN_LIMIT) / (MAX_LIMIT - MIN_LIMIT) * (1.0 - OFF_ZONE);
		}

		private double limit() {
			if (this.value < OFF_ZONE) {
				return 0.0;
			}
			double raw = MIN_LIMIT + (this.value - OFF_ZONE) / (1.0 - OFF_ZONE) * (MAX_LIMIT - MIN_LIMIT);
			return Math.round(raw / 5.0) * 5.0;
		}

		@Override
		protected void updateMessage() {
			double limit = this.limit();
			this.setMessage(limit <= 0 ? Component.translatable("voxelvelo.rider.limit_off") : Component.translatable("voxelvelo.rider.limit", Math.round(limit)));
		}

		@Override
		protected void applyValue() {
			RiderScreen.this.ctx.config.speedLimitKmh = this.limit();
		}
	}
}
