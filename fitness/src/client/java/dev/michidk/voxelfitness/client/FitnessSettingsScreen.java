package dev.michidk.voxelfitness.client;

import dev.michidk.voxvelo.client.ride.RideScreen;
import dev.michidk.voxvelo.client.ui.BluetoothScreen;
import dev.michidk.voxvelo.client.ui.ObcScreen;
import dev.michidk.voxvelo.client.ui.TrainerSettingsScreen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class FitnessSettingsScreen extends Screen {
	private final Screen parent;

	public FitnessSettingsScreen(Screen parent) {
		super(Component.translatable("voxel_fitness.settings.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int x = width / 2 - 100, y = 60;
		addRenderableWidget(
				Button.builder(
								Component.translatable("voxvelo.config.bluetooth_page"),
								b -> minecraft.gui.setScreen(new BluetoothScreen(this)))
						.bounds(x, y, 200, 20)
						.build());
		y += 24;
		addRenderableWidget(
				Button.builder(
								Component.translatable("voxvelo.config.obc"),
								b -> minecraft.gui.setScreen(new ObcScreen(this)))
						.bounds(x, y, 200, 20)
						.build());
		y += 24;
		addRenderableWidget(
				Button.builder(
								Component.translatable("voxvelo.trainer_settings.title"),
								b -> minecraft.gui.setScreen(new TrainerSettingsScreen(this)))
						.bounds(x, y, 200, 20)
						.build());
		y += 24;
		addRenderableWidget(
				Button.builder(
								Component.translatable("voxvelo.config.ride_recording"),
								b -> minecraft.gui.setScreen(new RideScreen(this)))
						.bounds(x, y, 200, 20)
						.build());
		addRenderableWidget(
				Button.builder(Component.translatable("gui.done"), b -> onClose())
						.bounds(x, height - 30, 200, 20)
						.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
		super.extractRenderState(graphics, x, y, delta);
		graphics.centeredText(font, title, width / 2, 20, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
