package dev.michidk.voxvelo.client.ui;

import dev.michidk.voxelfitness.client.FitnessRuntime;
import dev.michidk.voxelfitness.client.ui.Tips;
import dev.michidk.voxvelo.client.bluetooth.ConnectionState;
import dev.michidk.voxvelo.client.fitness.FitnessStatus;
import dev.michidk.voxvelo.client.obc.ObcDevice;
import dev.michidk.voxvelo.client.obc.ObcMode;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Picks the OpenBikeControl device (for example BikeControl on a phone) and the transports to use. */
public class ObcScreen extends Screen {
	private static final int MAX_ROWS = 4;
	private static final int WIDTH = 310;

	private final Screen parent;
	private final FitnessRuntime ctx = FitnessRuntime.get();
	private EditBox manual;
	private Button disconnectButton;
	private Button forgetButton;
	private String manualText = "";
	private String signature = "";
	private int listTop;

	public ObcScreen(Screen parent) {
		super(Component.translatable("voxvelo.obc.title"));
		this.parent = parent;
		this.ctx.obc.startDiscovery(this.ctx.config);
	}

	@Override
	protected void init() {
		int left = this.width / 2 - WIDTH / 2;
		int y = 60;

		this.addRenderableWidget(CycleButton.builder(ObcMode::label, this.ctx.config.obcMode)
			.withValues(ObcMode.values())
			.withTooltip(mode -> Tips.of("voxvelo.obc.mode"))
			.create(left, y, WIDTH, 20, Component.translatable("voxvelo.obc.mode"), (button, value) -> {
				this.ctx.config.obcMode = value;
				this.ctx.obc.rescan(this.ctx.config);
			}));
		y += 24;

		this.addRenderableWidget(Button.builder(Component.translatable("voxvelo.obc.rescan"), button -> this.ctx.obc.rescan(this.ctx.config))
			.tooltip(Tips.of("voxvelo.obc.rescan"))
			.bounds(left, y, 100, 20).build());
		this.disconnectButton = this.addRenderableWidget(Button.builder(Component.translatable("voxvelo.trainer.disconnect"), button -> this.ctx.obc.disconnect())
			.tooltip(Tips.of("voxvelo.trainer.disconnect"))
			.bounds(left + 105, y, 100, 20).build());
		this.forgetButton = this.addRenderableWidget(Button.builder(Component.translatable("voxvelo.trainer.forget"), button -> this.forget())
			.tooltip(Tips.of("voxvelo.trainer.forget"))
			.bounds(left + 210, y, 100, 20).build());
		this.updateButtons();
		y += 28;
		this.listTop = y;

		List<ObcDevice> devices = this.ctx.obc.discovered(this.ctx.config);
		for (int i = 0; i < Math.min(MAX_ROWS, devices.size()); i++) {
			ObcDevice device = devices.get(i);
			this.addRenderableWidget(Button.builder(this.rowLabel(device), button -> this.choose(device)).tooltip(Tips.of("voxvelo.obc.device")).bounds(left, y, WIDTH, 20).build());
			y += 24;
		}

		int bottom = this.height - 56;
		this.manual = this.addRenderableWidget(new EditBox(this.font, left, bottom, 200, 20, Component.translatable("voxvelo.obc.manual")));
		this.manual.setTooltip(Tips.of("voxvelo.obc.manual"));
		this.manual.setHint(Component.translatable("voxvelo.obc.manual_hint"));
		this.manual.setMaxLength(64);
		this.manual.setValue(this.manualText);
		this.manual.setResponder(text -> this.manualText = text);
		this.addRenderableWidget(Button.builder(Component.translatable("voxvelo.obc.connect"), button -> this.connectManual())
			.tooltip(Tips.of("voxvelo.obc.connect"))
			.bounds(left + 210, bottom, 100, 20).build());

		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
			.bounds(left, this.height - 30, WIDTH, 20).build());
		this.signature = this.currentSignature();
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

	/** Parses host:port (the port is required). Malformed input is rejected with a message. */
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
			this.choose(ObcDevice.manual(text.substring(0, split), port));
		} catch (NumberFormatException e) {
			if (this.minecraft.player != null) {
				this.minecraft.player.sendOverlayMessage(Component.translatable("voxvelo.obc.manual_invalid"));
			}
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
		StringBuilder sb = new StringBuilder();
		List<ObcDevice> devices = this.ctx.obc.discovered(this.ctx.config);
		for (int i = 0; i < Math.min(MAX_ROWS, devices.size()); i++) {
			sb.append('|').append(devices.get(i).key());
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
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int cx = this.width / 2;
		graphics.centeredText(this.font, this.title, cx, 20, 0xFFFFFFFF);
		graphics.centeredText(this.font, FitnessStatus.obcStatus(this.ctx), cx, 36, 0xFFC0C0C0);
		if (this.ctx.obc.mdns().failed() && this.ctx.config.obcMode.network()) {
			graphics.centeredText(this.font, Component.translatable("voxvelo.obc.network_unavailable"), cx, 48, 0xFFFF8080);
		} else {
			graphics.centeredText(this.font, Component.translatable("voxvelo.obc.hint"), cx, 48, 0xFF909090);
		}
		if (this.ctx.config.obcMode != ObcMode.OFF && this.ctx.obc.discovered(this.ctx.config).isEmpty()) {
			String key = this.ctx.obc.searching(this.ctx.config) ? "voxvelo.obc.searching" : "voxvelo.obc.none_found";
			graphics.centeredText(this.font, Component.translatable(key), cx, this.listTop + 6, 0xFF909090);
		}
	}

	@Override
	public void onClose() {
		this.ctx.config.save();
		this.ctx.obc.stopDiscovery();
		this.minecraft.gui.setScreen(this.parent);
	}
}
