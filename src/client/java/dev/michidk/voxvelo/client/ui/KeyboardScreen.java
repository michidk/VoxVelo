package dev.michidk.voxvelo.client.ui;

import dev.michidk.voxvelo.client.ClientContext;
import dev.michidk.voxvelo.client.config.VoxVeloConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Tunes keyboard riding: how many watts the pedal key stands for, and how sharply the steering keys turn. */
public class KeyboardScreen extends SettingsScreen {
	private final ClientContext ctx = ClientContext.get();

	public KeyboardScreen(Screen parent) {
		super(parent, Component.translatable("voxvelo.keyboard.title"));
	}

	@Override
	protected void addOptions() {
		this.rows.addRow(new ValueSlider(0, 0, SettingsList.WIDTH, VoxVeloConfig.MIN_VIRTUAL_POWER, VoxVeloConfig.MAX_VIRTUAL_POWER, 10.0,
			this.ctx.config.keyboardVirtualPower,
			v -> Component.translatable("voxvelo.config.virtual_power", Math.round(v)), v -> this.ctx.config.keyboardVirtualPower = v).tip("voxvelo.config.virtual_power"));
		this.rows.addRow(new ValueSlider(0, 0, SettingsList.WIDTH, VoxVeloConfig.MIN_STEERING_SENSITIVITY, VoxVeloConfig.MAX_STEERING_SENSITIVITY, 0.05,
			this.ctx.config.keyboardSteeringSensitivity,
			v -> Component.translatable("voxvelo.config.steering_sensitivity", Math.round(v * 100)), v -> this.ctx.config.keyboardSteeringSensitivity = v).tip("voxvelo.config.steering_sensitivity"));
	}

	@Override
	protected void save() {
		this.ctx.config.save();
	}
}
