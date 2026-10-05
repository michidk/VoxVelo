package dev.michidk.voxelvelo.client.ui;

import dev.michidk.voxelvelo.client.ClientContext;
import dev.michidk.voxelvelo.client.bluetooth.BluetoothDevice;
import dev.michidk.voxelvelo.client.ftms.Ftms;
import dev.michidk.voxelvelo.terrain.TerrainSlope;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Scans for Bluetooth devices and lets the player pick the trainer. Everything here is non-blocking. */
public class TrainerScreen extends Screen {
	private static final int MAX_ROWS = 3;
	private static final int WIDTH = 310;

	private final Screen parent;
	private final ClientContext ctx = ClientContext.get();
	private String signature = "";

	public TrainerScreen(Screen parent) {
		super(Component.translatable("voxelvelo.trainer.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - WIDTH / 2;
		int y = 60;

		this.addRenderableWidget(Button.builder(this.scanLabel(), button -> this.toggleScan()).bounds(left, y, 100, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("voxelvelo.trainer.disconnect"), button -> this.ctx.ftms.disconnect())
			.bounds(left + 105, y, 100, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("voxelvelo.trainer.forget"), button -> this.forget())
			.bounds(left + 210, y, 100, 20).build());
		y += 28;

		List<BluetoothDevice> devices = this.ctx.bluetooth.devices(Ftms.SERVICE);
		for (int i = 0; i < Math.min(MAX_ROWS, devices.size()); i++) {
			BluetoothDevice device = devices.get(i);
			this.addRenderableWidget(Button.builder(this.rowLabel(device), button -> this.choose(device)).bounds(left, y, WIDTH, 20).build());
			y += 24;
		}

		// Connection and feedback settings, pinned above the Done button.
		int half = (WIDTH - 10) / 2;
		int right = left + half + 10;
		int by = this.height - 30 - 48;
		this.addRenderableWidget(CycleButton.onOffBuilder(this.ctx.config.autoConnectTrainer)
			.create(left, by, half, 20, Component.translatable("voxelvelo.config.auto_connect"), (button, value) -> this.ctx.config.autoConnectTrainer = value));
		this.addRenderableWidget(CycleButton.onOffBuilder(this.ctx.config.trainerResistanceEnabled)
			.create(right, by, half, 20, Component.translatable("voxelvelo.config.resistance"), (button, value) -> this.ctx.config.trainerResistanceEnabled = value));
		this.addRenderableWidget(new ValueSlider(left, by + 24, half, 0.0, 2.0, 0.1, this.ctx.config.trainerResistanceIntensity,
			v -> Component.translatable("voxelvelo.config.intensity", Math.round(v * 100)), v -> this.ctx.config.trainerResistanceIntensity = v));
		this.addRenderableWidget(new ValueSlider(right, by + 24, half, TerrainSlope.MIN_WINDOW, TerrainSlope.MAX_WINDOW, 1.0, this.ctx.config.slopeWindowBlocks,
			v -> Component.translatable("voxelvelo.config.slope_window", Math.round(v)), v -> this.ctx.config.slopeWindowBlocks = (int) Math.round(v)));

		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
			.bounds(left, this.height - 30, WIDTH, 20).build());
		this.signature = this.currentSignature();
	}

	private Component scanLabel() {
		return Component.translatable(this.ctx.bluetooth.isScanning() ? "voxelvelo.trainer.scanning" : "voxelvelo.trainer.scan");
	}

	private Component rowLabel(BluetoothDevice device) {
		boolean ftms = device.advertises(Ftms.SERVICE);
		String text = (ftms ? "★ " : "") + device.displayName() + "  (" + device.rssi() + " dBm)";
		return Component.literal(text);
	}

	private void toggleScan() {
		if (this.ctx.bluetooth.isScanning()) {
			this.ctx.bluetooth.stopScan();
		} else {
			this.ctx.bluetooth.scan(20_000);
		}
	}

	private void choose(BluetoothDevice device) {
		this.ctx.config.preferredTrainerId = device.id();
		this.ctx.config.preferredTrainerName = device.displayName();
		this.ctx.config.save();
		this.ctx.bluetooth.stopScan();
		this.ctx.ftms.connect(device);
	}

	private void forget() {
		this.ctx.ftms.disconnect();
		this.ctx.config.preferredTrainerId = "";
		this.ctx.config.preferredTrainerName = "";
		this.ctx.config.save();
	}

	private String currentSignature() {
		StringBuilder sb = new StringBuilder(this.ctx.bluetooth.isScanning() ? "S" : "-");
		List<BluetoothDevice> devices = this.ctx.bluetooth.devices(Ftms.SERVICE);
		for (int i = 0; i < Math.min(MAX_ROWS, devices.size()); i++) {
			BluetoothDevice device = devices.get(i);
			sb.append('|').append(device.id()).append(device.name()).append(device.rssi() / 5);
		}
		return sb.toString();
	}

	@Override
	public void tick() {
		super.tick();
		String now = this.currentSignature();
		if (!now.equals(this.signature)) {
			this.rebuildWidgets();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int cx = this.width / 2;
		graphics.centeredText(this.font, this.title, cx, 20, 0xFFFFFFFF);
		graphics.centeredText(this.font, this.statusLine(), cx, 36, 0xFFC0C0C0);
		if (!this.ctx.bluetooth.isAvailable()) {
			graphics.centeredText(this.font, Component.translatable("voxelvelo.trainer.unavailable", this.ctx.bluetooth.unavailableReason()), cx, 48, 0xFFFF8080);
		} else {
			graphics.centeredText(this.font, Component.translatable("voxelvelo.trainer.hint"), cx, 48, 0xFF909090);
		}
	}

	private Component statusLine() {
		return ConfigScreen.trainerStatus(this.ctx);
	}

	@Override
	public void onClose() {
		this.ctx.config.save();
		this.ctx.bluetooth.stopScan();
		this.minecraft.gui.setScreen(this.parent);
	}
}
