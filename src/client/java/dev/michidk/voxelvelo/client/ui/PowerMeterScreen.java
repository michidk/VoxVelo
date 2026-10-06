package dev.michidk.voxelvelo.client.ui;

import dev.michidk.voxelvelo.client.bluetooth.BluetoothDevice;
import dev.michidk.voxelvelo.client.fitness.FitnessContext;
import dev.michidk.voxelvelo.client.fitness.FitnessStatus;
import dev.michidk.voxelvelo.client.powermeter.CyclingPower;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Selects a secondary BLE Cycling Power Service sensor independently from the FTMS trainer. */
public class PowerMeterScreen extends Screen {
	private static final int MAX_ROWS = 5;
	private static final int WIDTH = 310;

	private final Screen parent;
	private final FitnessContext ctx = FitnessContext.get();
	private String signature = "";

	public PowerMeterScreen(Screen parent) {
		super(Component.translatable("voxelvelo.power_meter.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - WIDTH / 2;
		int y = 60;
		this.addRenderableWidget(Button.builder(this.scanLabel(), button -> this.toggleScan())
			.tooltip(Tips.of("voxelvelo.power_meter.scan")).bounds(left, y, 100, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("voxelvelo.power_meter.disconnect"), button -> this.ctx.powerMeter.disconnect())
			.tooltip(Tips.of("voxelvelo.power_meter.disconnect")).bounds(left + 105, y, 100, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("voxelvelo.power_meter.forget"), button -> this.forget())
			.tooltip(Tips.of("voxelvelo.power_meter.forget")).bounds(left + 210, y, 100, 20).build());
		y += 28;

		List<BluetoothDevice> devices = this.devices();
		int visibleRows = Math.max(0, Math.min(MAX_ROWS, (this.height - 82 - y) / 24));
		for (int i = 0; i < Math.min(visibleRows, devices.size()); i++) {
			BluetoothDevice device = devices.get(i);
			this.addRenderableWidget(Button.builder(this.rowLabel(device), button -> this.choose(device))
				.tooltip(Tips.of("voxelvelo.power_meter.device")).bounds(left, y, WIDTH, 20).build());
			y += 24;
		}

		this.addRenderableWidget(CycleButton.onOffBuilder(this.ctx.config.autoConnectPowerMeter)
			.withTooltip(value -> Tips.of("voxelvelo.config.power_meter_auto_connect"))
			.create(left, this.height - 54, WIDTH, 20, Component.translatable("voxelvelo.config.power_meter_auto_connect"),
				(button, value) -> this.ctx.config.autoConnectPowerMeter = value));
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
			.bounds(left, this.height - 30, WIDTH, 20).build());
		this.signature = this.currentSignature();
	}

	private List<BluetoothDevice> devices() {
		// This connection is deliberately secondary; do not offer the already selected trainer itself.
		return this.ctx.bluetooth.devices(CyclingPower.SERVICE).stream()
			.filter(device -> !device.id().equals(this.ctx.config.preferredTrainerId))
			.toList();
	}

	private Component scanLabel() {
		return Component.translatable(this.ctx.bluetooth.isScanning()
			? "voxelvelo.power_meter.scanning" : "voxelvelo.power_meter.scan");
	}

	private Component rowLabel(BluetoothDevice device) {
		String text = (device.advertises(CyclingPower.SERVICE) ? "★ " : "")
			+ device.displayName() + "  (" + device.rssi() + " dBm)";
		return Component.literal(text);
	}

	private void toggleScan() {
		if (this.ctx.bluetooth.isScanning()) this.ctx.bluetooth.stopScan();
		else this.ctx.bluetooth.scan(20_000);
	}

	private void choose(BluetoothDevice device) {
		this.ctx.config.preferredPowerMeterId = device.id();
		this.ctx.config.preferredPowerMeterName = device.displayName();
		this.ctx.config.save();
		this.ctx.bluetooth.stopScan();
		this.ctx.powerMeter.connect(device);
	}

	private void forget() {
		this.ctx.powerMeter.disconnect();
		this.ctx.config.preferredPowerMeterId = "";
		this.ctx.config.preferredPowerMeterName = "";
		this.ctx.config.save();
	}

	private String currentSignature() {
		StringBuilder sb = new StringBuilder(this.ctx.bluetooth.isScanning() ? "S" : "-");
		List<BluetoothDevice> devices = this.devices();
		for (int i = 0; i < Math.min(MAX_ROWS, devices.size()); i++) {
			BluetoothDevice device = devices.get(i);
			sb.append('|').append(device.id()).append(device.name()).append(device.rssi() / 5);
		}
		return sb.toString();
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.currentSignature().equals(this.signature)) this.rebuildWidgets();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int cx = this.width / 2;
		graphics.centeredText(this.font, this.title, cx, 20, 0xFFFFFFFF);
		graphics.centeredText(this.font, FitnessStatus.powerMeterStatus(this.ctx), cx, 36, 0xFFC0C0C0);
		if (!this.ctx.bluetooth.isAvailable()) {
			graphics.centeredText(this.font, Component.translatable("voxelvelo.power_meter.unavailable", this.ctx.bluetooth.unavailableReason()),
				cx, 48, 0xFFFF8080);
		} else {
			graphics.centeredText(this.font, Component.translatable("voxelvelo.power_meter.hint"), cx, 48, 0xFF909090);
		}
	}

	@Override
	public void onClose() {
		this.ctx.config.save();
		this.ctx.bluetooth.stopScan();
		this.minecraft.gui.setScreen(this.parent);
	}
}
