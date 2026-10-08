package dev.michidk.voxvelo.client.ui;

import dev.michidk.voxelfitness.client.FitnessRuntime;
import dev.michidk.voxelfitness.client.ui.SettingsList;
import dev.michidk.voxelfitness.client.ui.SettingsScreen;
import dev.michidk.voxelfitness.client.ui.Tips;
import dev.michidk.voxvelo.client.bluetooth.ConnectionState;
import dev.michidk.voxvelo.client.fitness.FitnessStatus;
import dev.michidk.voxvelo.client.obc.ObcDevice;
import dev.michidk.voxvelo.client.obc.ObcMode;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Picks the OpenBikeControl device (for example BikeControl on a phone) and the transports to use. */
public class ObcScreen extends SettingsScreen {
	private static final int WIDTH = SettingsList.WIDTH;

	private final FitnessRuntime ctx = FitnessRuntime.get();
	private Button disconnectButton;
	private Button forgetButton;
	private String manualText = "";
	/** Why the typed address was refused, shown under it until it is edited. */
	private @Nullable Component manualError;
	private String signature = "";

	public ObcScreen(Screen parent) {
		super(parent, Component.translatable("voxvelo.obc.title"));
		this.ctx.obc.startDiscovery(this.ctx.config);
	}

	@Override
	protected void addOptions() {
		this.rows.addText(() -> FitnessStatus.obcStatus(this.ctx), STATUS);
		this.rows.addText(this::hint, HINT);

		this.rows.addRow(CycleButton.builder(ObcMode::label, this.ctx.config.obcMode)
			.withValues(ObcMode.values())
			.withTooltip(mode -> Tips.of("voxvelo.obc.mode"))
			.create(0, 0, WIDTH, 20, Component.translatable("voxvelo.obc.mode"), (button, value) -> {
				this.ctx.config.obcMode = value;
				this.ctx.obc.rescan(this.ctx.config);
			}));

		Button rescan = Button.builder(Component.translatable("voxvelo.obc.rescan"), button -> this.ctx.obc.rescan(this.ctx.config))
			.tooltip(Tips.of("voxvelo.obc.rescan"))
			.bounds(0, 0, 100, 20).build();
		this.disconnectButton = Button.builder(Component.translatable("voxvelo.trainer.disconnect"), button -> this.ctx.obc.disconnect())
			.tooltip(Tips.of("voxvelo.trainer.disconnect"))
			.bounds(105, 0, 100, 20).build();
		this.forgetButton = Button.builder(Component.translatable("voxvelo.trainer.forget"), button -> this.forget())
			.tooltip(Tips.of("voxvelo.trainer.forget"))
			.bounds(210, 0, 100, 20).build();
		this.rows.addRow(rescan, this.disconnectButton, this.forgetButton);
		this.updateButtons();

		List<ObcDevice> devices = this.ctx.obc.discovered(this.ctx.config);
		if (this.ctx.config.obcMode != ObcMode.OFF && devices.isEmpty()) {
			this.rows.addText(() -> Component.translatable(this.ctx.obc.searching(this.ctx.config) ? "voxvelo.obc.searching" : "voxvelo.obc.none_found"), HINT);
		}
		for (ObcDevice device : devices) {
			this.rows.addRow(Button.builder(this.rowLabel(device), button -> this.choose(device))
				.tooltip(Tips.of("voxvelo.obc.device")).bounds(0, 0, WIDTH, 20).build());
		}

		EditBox manual = new EditBox(this.font, 0, 0, 200, 20, Component.translatable("voxvelo.obc.manual"));
		manual.setTooltip(Tips.of("voxvelo.obc.manual"));
		manual.setHint(Component.translatable("voxvelo.obc.manual_hint"));
		manual.setMaxLength(64);
		manual.setValue(this.manualText);
		manual.setResponder(text -> {
			this.manualText = text;
			this.manualError = null;
		});
		this.rows.addRow(manual, Button.builder(Component.translatable("voxvelo.obc.connect"), button -> this.connectManual())
			.tooltip(Tips.of("voxvelo.obc.connect"))
			.bounds(210, 0, 100, 20).build());
		this.rows.addText(() -> this.manualError, ERROR);
		this.signature = this.currentSignature();
	}

	/** Why network discovery cannot work, or else how this page works. */
	private Component hint() {
		if (this.ctx.obc.mdns().failed() && this.ctx.config.obcMode.network()) {
			return Component.translatable("voxvelo.obc.network_unavailable").withColor(ERROR & 0xFFFFFF);
		}
		return Component.translatable("voxvelo.obc.hint");
	}

	private Component rowLabel(ObcDevice device) {
		ObcMode transport = device.transport() == ObcDevice.Transport.NETWORK ? ObcMode.NETWORK : ObcMode.BLUETOOTH;
		return Component.translatable("voxvelo.obc.device_row", device.name(), transport.label());
	}

	private void choose(ObcDevice device) {
		this.ctx.config.obcPreferredKey = device.key();
		this.ctx.config.obcPreferredName = device.name();
		this.ctx.config.save();
		this.ctx.obc.connect(device);
	}

	/** Parses host:port (the port is required). Malformed input is refused with a message under the field. */
	private void connectManual() {
		String text = this.manualText.trim();
		int split = text.lastIndexOf(':');
		try {
			if (split <= 0) {
				throw new NumberFormatException();
			}
			int port = Integer.parseInt(text.substring(split + 1));
			if (port < 1 || port > 65535) {
				throw new NumberFormatException();
			}
			this.manualError = null;
			this.choose(ObcDevice.manual(text.substring(0, split), port));
		} catch (NumberFormatException e) {
			this.manualError = Component.translatable("voxvelo.obc.manual_invalid");
		}
	}

	private void forget() {
		this.ctx.obc.disconnect();
		this.ctx.config.obcPreferredKey = "";
		this.ctx.config.obcPreferredName = "";
		this.ctx.config.save();
	}

	/** Disconnect only applies while a connection exists or is being made; Forget only while a device is saved. */
	private void updateButtons() {
		this.disconnectButton.active = this.ctx.obc.state() != ConnectionState.DISCONNECTED;
		this.forgetButton.active = !this.ctx.config.obcPreferredKey.isEmpty();
	}

	private String currentSignature() {
		StringBuilder sb = new StringBuilder(this.ctx.config.obcMode.name());
		for (ObcDevice device : this.ctx.obc.discovered(this.ctx.config)) {
			sb.append('|').append(device.key());
		}
		return sb.toString();
	}

	@Override
	public void tick() {
		super.tick();
		this.updateButtons();
		if (!this.currentSignature().equals(this.signature)) {
			this.rebuildWidgets();
		}
	}

	@Override
	protected void save() {
		this.ctx.config.save();
	}

	@Override
	public void onClose() {
		this.ctx.obc.stopDiscovery();
		super.onClose();
	}
}
