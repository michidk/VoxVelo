package dev.michidk.voxvelo.client.ui;

import dev.michidk.voxvelo.client.ClientContext;
import dev.michidk.voxvelo.client.config.VoxVeloConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Tunes keyboard riding: how many watts the pedal key stands for, and how sharply the steering keys turn. */
public class KeyboardScreen extends Screen {
	private static final int WIDTH = 310;

	private final Screen parent;
	private final ClientContext ctx = ClientContext.get();

	public KeyboardScreen(Screen parent) {
		super(Component.translatable("voxvelo.keyboard.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - WIDTH / 2;
		int y = 46;
		this.addRenderableWidget(new ValueSlider(left, y, WIDTH, VoxVeloConfig.MIN_VIRTUAL_POWER, VoxVeloConfig.MAX_VIRTUAL_POWER, 10.0,
			this.ctx.config.keyboardVirtualPower,
			v -> Component.translatable("voxvelo.config.virtual_power", Math.round(v)), v -> this.ctx.config.keyboardVirtualPower = v).tip("voxvelo.config.virtual_power"));
		y += 24;
		this.addRenderableWidget(new ValueSlider(left, y, WIDTH, VoxVeloConfig.MIN_STEERING_SENSITIVITY, VoxVeloConfig.MAX_STEERING_SENSITIVITY, 0.05,
			this.ctx.config.keyboardSteeringSensitivity,
			v -> Component.translatable("voxvelo.config.steering_sensitivity", Math.round(v * 100)), v -> this.ctx.config.keyboardSteeringSensitivity = v).tip("voxvelo.config.steering_sensitivity"));

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
