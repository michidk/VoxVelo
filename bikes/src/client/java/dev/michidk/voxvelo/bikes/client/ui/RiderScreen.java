package dev.michidk.voxvelo.bikes.client.ui;

import dev.michidk.voxvelo.bikes.bike.RiderSettings;
import dev.michidk.voxvelo.bikes.client.ClientContext;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Rider mass and personal speed limit. Both are sent to the server (and clamped there). */
public class RiderScreen extends SettingsScreen {
	private static final int WIDTH = SettingsList.WIDTH;
	/** The first slice of the limit slider means "no limit". */
	private static final double OFF_ZONE = 0.05;

	private final ClientContext ctx = ClientContext.get();

	public RiderScreen(Screen parent) {
		super(parent, Component.translatable("voxvelo_bikes.rider.title"));
	}

	@Override
	protected void addOptions() {
		this.rows.addText(() -> Component.translatable("voxvelo_bikes.rider.note"), HINT);
		this.rows.addRow(new MassSlider(0, 0));
		this.rows.addRow(new LimitSlider(0, 0));
	}

	@Override
	protected void save() {
		this.ctx.config.save();
		this.ctx.sendRiderSettings();
	}

	private final class MassSlider extends AbstractSliderButton {
		MassSlider(int x, int y) {
			super(x, y, WIDTH, 20, Component.empty(), (RiderScreen.this.ctx.config.riderMassKg - RiderSettings.MIN_MASS_KG) / (RiderSettings.MAX_MASS_KG - RiderSettings.MIN_MASS_KG));
			this.updateMessage();
			this.setTooltip(Tips.of("voxvelo_bikes.rider.mass"));
		}

		private double mass() {
			return Math.round(RiderSettings.MIN_MASS_KG + this.value * (RiderSettings.MAX_MASS_KG - RiderSettings.MIN_MASS_KG));
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.translatable("voxvelo_bikes.rider.mass", Math.round(this.mass())));
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
			this.setTooltip(Tips.of("voxvelo_bikes.rider.limit"));
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
			this.setMessage(limit <= 0 ? Component.translatable("voxvelo_bikes.rider.limit_off") : Component.translatable("voxvelo_bikes.rider.limit", Math.round(limit)));
		}

		@Override
		protected void applyValue() {
			RiderScreen.this.ctx.config.speedLimitKmh = this.limit();
		}
	}
}
