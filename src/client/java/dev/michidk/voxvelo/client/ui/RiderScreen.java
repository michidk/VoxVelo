package dev.michidk.voxvelo.client.ui;

import dev.michidk.voxvelo.bike.RiderSettings;
import dev.michidk.voxvelo.client.ClientContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Rider mass and personal speed limit. Both are sent to the server (and clamped there). */
public class RiderScreen extends Screen {
	private static final int WIDTH = 310;
	/** The first slice of the limit slider means "no limit". */
	private static final double OFF_ZONE = 0.05;

	private final Screen parent;
	private final ClientContext ctx = ClientContext.get();

	public RiderScreen(Screen parent) {
		super(Component.translatable("voxvelo.rider.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - WIDTH / 2;
		int y = 46;
		this.addRenderableWidget(new MassSlider(left, y));
		y += 24;
		this.addRenderableWidget(new LimitSlider(left, y));
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
			.bounds(left, this.height - 30, WIDTH, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
		graphics.centeredText(this.font, Component.translatable("voxvelo.rider.note"), this.width / 2, 33, 0xFF909090);
	}

	@Override
	public void onClose() {
		this.ctx.config.save();
		this.ctx.sendRiderSettings();
		this.minecraft.gui.setScreen(this.parent);
	}

	private final class MassSlider extends AbstractSliderButton {
		MassSlider(int x, int y) {
			super(x, y, WIDTH, 20, Component.empty(), (RiderScreen.this.ctx.config.riderMassKg - RiderSettings.MIN_MASS_KG) / (RiderSettings.MAX_MASS_KG - RiderSettings.MIN_MASS_KG));
			this.updateMessage();
			this.setTooltip(Tips.of("voxvelo.rider.mass"));
		}

		private double mass() {
			return Math.round(RiderSettings.MIN_MASS_KG + this.value * (RiderSettings.MAX_MASS_KG - RiderSettings.MIN_MASS_KG));
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.translatable("voxvelo.rider.mass", Math.round(this.mass())));
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
			this.setTooltip(Tips.of("voxvelo.rider.limit"));
		}

		private static double initial(double limit) {
			return limit <= 0 ? 0.0 : OFF_ZONE + (limit - RiderSettings.MIN_LIMIT_KMH) / (RiderSettings.MAX_LIMIT_KMH - RiderSettings.MIN_LIMIT_KMH) * (1.0 - OFF_ZONE);
		}

		private double limit() {
			if (this.value < OFF_ZONE) {
				return 0.0;
			}
			double raw = RiderSettings.MIN_LIMIT_KMH + (this.value - OFF_ZONE) / (1.0 - OFF_ZONE) * (RiderSettings.MAX_LIMIT_KMH - RiderSettings.MIN_LIMIT_KMH);
			return Math.round(raw / 5.0) * 5.0;
		}

		@Override
		protected void updateMessage() {
			double limit = this.limit();
			this.setMessage(limit <= 0 ? Component.translatable("voxvelo.rider.limit_off") : Component.translatable("voxvelo.rider.limit", Math.round(limit)));
		}

		@Override
		protected void applyValue() {
			RiderScreen.this.ctx.config.speedLimitKmh = this.limit();
		}
	}
}
