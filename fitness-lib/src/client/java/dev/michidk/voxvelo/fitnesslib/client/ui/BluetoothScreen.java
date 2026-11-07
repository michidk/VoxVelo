package dev.michidk.voxvelo.fitnesslib.client.ui;

import dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime;
import dev.michidk.voxvelo.fitnesslib.client.bluetooth.BluetoothDevice;
import dev.michidk.voxvelo.fitnesslib.client.bluetooth.BluetoothDevices;
import dev.michidk.voxvelo.fitnesslib.client.bluetooth.BluetoothDevices.Capability;
import dev.michidk.voxvelo.fitnesslib.client.bluetooth.BluetoothDevices.Device;
import dev.michidk.voxvelo.fitnesslib.client.bluetooth.ConnectionState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Device connections and exclusive capability sources, with a separate list of nearby peripherals. */
public class BluetoothScreen extends SettingsScreen {
	private static final int WIDTH = SettingsList.WIDTH;
	private final FitnessRuntime ctx = FitnessRuntime.get();
	private final BluetoothDevices devices = this.ctx.bluetoothDevices;
	private String signature = "";

	public BluetoothScreen(Screen parent) {
		super(parent, text("title"));
	}

	private static Component text(String key) {
		return Component.translatable("voxvelo_fitness_lib.bluetooth." + key);
	}

	@Override
	protected void addOptions() {
		this.rows.addText(() -> this.ctx.bluetooth.isAvailable() ? text("hint") :
			Component.translatable("voxvelo_fitness_lib.bluetooth.unavailable", this.ctx.bluetooth.unavailableReason()).withColor(ERROR), HINT);
		this.section("saved");
		if (this.devices.saved().isEmpty()) this.rows.addText(() -> text("saved_empty"), HINT);
		for (Device device : this.devices.saved()) this.saved(device);

		this.rows.addRow(Button.builder(text(this.ctx.bluetooth.isScanning() ? "scanning" : "scan"), button -> {
			if (this.ctx.bluetooth.isScanning()) this.ctx.bluetooth.stopScan();
			else this.devices.scan();
		}).tooltip(Tips.of("voxvelo_fitness_lib.bluetooth.scan")).bounds(0, 0, WIDTH, 20).build());
		this.section("nearby");
		List<BluetoothDevice> nearby = this.devices.nearby();
		if (nearby.isEmpty()) this.rows.addText(() -> text("nearby_empty"), HINT);
		for (BluetoothDevice device : nearby) this.nearby(device);

		this.rows.addRow(CycleButton.onOffBuilder(this.ctx.config.autoConnectBluetooth)
			.withTooltip(value -> Tips.of("voxvelo_fitness_lib.config.auto_connect"))
			.create(0, 0, SettingsList.HALF, 20, Component.translatable("voxvelo_fitness_lib.config.auto_connect"),
				(button, value) -> {
					this.ctx.config.autoConnectBluetooth = value;
					this.ctx.config.autoConnectTrainer = value;
					this.ctx.config.autoConnectPowerMeter = value;
					this.ctx.config.autoConnectHeartRate = value;
				}),
			Button.builder(text("trainer_settings"), button -> this.minecraft.gui.setScreen(new TrainerSettingsScreen(this)))
				.tooltip(Tips.of("voxvelo_fitness_lib.bluetooth.trainer_settings")).bounds(WIDTH - SettingsList.HALF, 0, SettingsList.HALF, 20).build());
		this.signature = this.currentSignature();
	}

	private void section(String key) {
		this.rows.addText((graphics, font, left, top) -> {
			graphics.text(font, text(key), left + 4, top + 2, STATUS);
			int start = left + font.width(text(key)) + 12;
			graphics.fill(start, top + 6, left + WIDTH - 4, top + 7, 0xFF52606B);
		});
	}

	private void saved(Device device) {
		boolean open = device.state() != ConnectionState.DISCONNECTED;
		List<AbstractWidget> actions = List.of(
			Button.builder(open ? Component.translatable("voxvelo_fitness_lib.trainer.disconnect") : text("connect"), button -> {
				if (open) this.devices.disconnect(device); else device.connect();
			}).bounds(WIDTH - 124, 0, 68, 20).build(),
			Button.builder(Component.translatable("voxvelo_fitness_lib.trainer.forget"), button -> this.devices.forget(device))
				.tooltip(Tooltip.create(text("forget.tip"))).bounds(WIDTH - 54, 0, 54, 20).build());
		this.rows.addRow(actions, (graphics, font, left, top) -> {
			panel(graphics, left, top, 25);
			graphics.text(font, this.fit(device.descriptor().displayName(), WIDTH - 138), left + 7, top + 2, 0xFFFFFFFF);
			Component state = Component.translatable("voxvelo_fitness_lib.connection.state." + device.state().name().toLowerCase(java.util.Locale.ROOT));
			Component message = device.message();
			for (Capability capability : device.capabilities()) {
				if (this.devices.selected(device, capability) && this.devices.client(capability).message() != null) {
					message = this.devices.client(capability).message();
					break;
				}
			}
			graphics.text(font, this.fit(message == null ? state.getString() : message.getString(), WIDTH - 138), left + 7, top + 13,
				device.isConnected() && message == null ? 0xFF8EE3B0 : STATUS);
		});
		List<AbstractWidget> badges = new ArrayList<>();
		int x = 6;
		for (Capability capability : device.capabilities()) {
			boolean selected = this.devices.selected(device, capability);
			boolean failed = selected && this.devices.client(capability).state() == ConnectionState.DISCONNECTED && this.devices.client(capability).message() != null;
			Component label = Component.literal(failed ? "[!] " : selected ? "[x] " : "[ ] ").append(text("role." + capability.key));
			int width = this.font.width(label) + 12;
			Badge badge = new Badge(x, width, label, capability.color, selected, button -> this.devices.select(device, capability));
			badge.active = true;
			Component error = this.devices.client(capability).message();
			badge.setTooltip(Tooltip.create(failed ? error.copy().append("\n").append(text("retry.tip")) : selected && error != null ? error :
				Component.translatable("voxvelo_fitness_lib.bluetooth.badge.tip", text("role." + capability.key))));
			badges.add(badge);
			x += width + 5;
		}
		this.rows.addRow(badges, (graphics, font, left, top) -> panel(graphics, left, top, 21));
	}

	private void nearby(BluetoothDevice device) {
		this.rows.addRow(List.of(Button.builder(text("connect"), button -> this.devices.connect(device))
			.bounds(WIDTH - 68, 0, 68, 20).build()), (graphics, font, left, top) -> {
			panel(graphics, left, top, 25);
			graphics.text(font, this.fit(device.displayName(), WIDTH - 122), left + 7, top + 2, 0xFFFFFFFF);
			String signal = device.rssi() + "";
			graphics.text(font, signal, left + WIDTH - 77 - font.width(signal), top + 6, HINT);
		});
		this.rows.addRow(List.of(), (graphics, font, left, top) -> {
			panel(graphics, left, top, 21);
			int x = left + 6;
			for (Capability capability : Capability.values()) {
				if (!device.advertises(capability.service)) continue;
				Component label = text("role." + capability.key);
				int width = font.width(label) + 12;
				graphics.fill(x, top + 1, x + width, top + 17, 0xFF28323C);
				graphics.fill(x, top + 15, x + width, top + 17, capability.color);
				graphics.text(font, label, x + 6, top + 4, capability.color);
				x += width + 5;
			}
		});
	}

	private static void panel(GuiGraphicsExtractor graphics, int left, int top, int height) {
		graphics.fill(left, top, left + WIDTH, top + height, 0xB0182028);
	}

	/** Compact colored toggle with a visible check mark and vanilla keyboard and narration behavior. */
	private static final class Badge extends Button {
		private final int color;
		private final boolean selected;

		Badge(int x, int width, Component label, int color, boolean selected, OnPress press) {
			super(x, 0, width, 18, label, press, DEFAULT_NARRATION);
			this.color = color;
			this.selected = selected;
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			int x = this.getX();
			int y = this.getY();
			int edge = this.isHoveredOrFocused() && this.active ? 0xFFFFFFFF : this.color;
			graphics.fill(x, y, x + this.getWidth(), y + 18, edge);
			graphics.fill(x + 1, y + 1, x + this.getWidth() - 1, y + 17, this.selected ? 0xFF344450 : 0xFF202A33);
			graphics.centeredText(Minecraft.getInstance().font, this.getMessage(), x + this.getWidth() / 2, y + 5, this.color);
		}
	}

	private String currentSignature() {
		StringBuilder signature = new StringBuilder().append(this.ctx.bluetooth.isAvailable()).append(this.ctx.bluetooth.isScanning());
		for (Device device : this.devices.saved()) signature.append(device.descriptor()).append(device.state()).append(device.message());
		for (Capability capability : Capability.values()) {
			signature.append(this.devices.selectedId(capability)).append(this.devices.client(capability).state()).append(this.devices.client(capability).message());
		}
		for (BluetoothDevice device : this.devices.nearby()) signature.append(device.id()).append(device.name()).append(device.advertisedServices());
		return signature.toString();
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.currentSignature().equals(this.signature)) this.rebuildWidgets();
	}

	private String fit(String text, int maxWidth) {
		return this.font.width(text) <= maxWidth ? text : this.font.plainSubstrByWidth(text, maxWidth - this.font.width("...")) + "...";
	}

	@Override
	protected void save() { this.ctx.config.save(); }

	@Override
	public void onClose() {
		this.ctx.bluetooth.stopScan();
		super.onClose();
	}
}
