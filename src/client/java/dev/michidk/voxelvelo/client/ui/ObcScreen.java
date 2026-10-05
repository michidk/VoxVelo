package dev.michidk.voxelvelo.client.ui;

import dev.michidk.voxelvelo.client.fitness.FitnessContext;
import dev.michidk.voxelvelo.client.fitness.FitnessStatus;
import dev.michidk.voxelvelo.client.obc.ObcDevice;
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
	private final FitnessContext ctx = FitnessContext.get();
	private EditBox manual;
	private String manualText = "";
	private String signature = "";

	public ObcScreen(Screen parent) {
		super(Component.translatable("voxelvelo.obc.title"));
		this.parent = parent;
		this.ctx.obc.startDiscovery(this.ctx.config);
	}

	@Override
	protected void init() {
		int left = this.width / 2 - WIDTH / 2;
		int y = 60;

		this.addRenderableWidget(CycleButton.<String>builder(mode -> Component.translatable("voxelvelo.obc.mode." + mode), this.ctx.config.obcMode)
			.withValues("off", "network", "bluetooth", "both")
			.withTooltip(mode -> Tips.of("voxelvelo.obc.mode"))
			.create(left, y, WIDTH, 20, Component.translatable("voxelvelo.obc.mode"), (button, value) -> {
				this.ctx.config.obcMode = value;
				this.ctx.obc.stopDiscovery();
				this.ctx.obc.startDiscovery(this.ctx.config);
			}));
		y += 24;

		this.addRenderableWidget(Button.builder(Component.translatable("voxelvelo.trainer.disconnect"), button -> this.ctx.obc.disconnect())
			.tooltip(Tips.of("voxelvelo.trainer.disconnect"))
			.bounds(left, y, 150, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("voxelvelo.trainer.forget"), button -> this.forget())
			.tooltip(Tips.of("voxelvelo.trainer.forget"))
			.bounds(left + 160, y, 150, 20).build());
		y += 28;

		List<ObcDevice> devices = this.ctx.obc.discovered(this.ctx.config);
		for (int i = 0; i < Math.min(MAX_ROWS, devices.size()); i++) {
			ObcDevice device = devices.get(i);
			this.addRenderableWidget(Button.builder(this.rowLabel(device), button -> this.choose(device)).tooltip(Tips.of("voxelvelo.obc.device")).bounds(left, y, WIDTH, 20).build());
			y += 24;
		}

		int bottom = this.height - 56;
		this.manual = this.addRenderableWidget(new EditBox(this.font, left, bottom, 200, 20, Component.translatable("voxelvelo.obc.manual")));
		this.manual.setTooltip(Tips.of("voxelvelo.obc.manual"));
		this.manual.setHint(Component.translatable("voxelvelo.obc.manual_hint"));
		this.manual.setMaxLength(64);
		this.manual.setValue(this.manualText);
		this.manual.setResponder(text -> this.manualText = text);
		this.addRenderableWidget(Button.builder(Component.translatable("voxelvelo.obc.connect"), button -> this.connectManual())
			.tooltip(Tips.of("voxelvelo.obc.connect"))
			.bounds(left + 210, bottom, 100, 20).build());

		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
			.bounds(left, this.height - 30, WIDTH, 20).build());
		this.signature = this.currentSignature();
	}

	private Component rowLabel(ObcDevice device) {
		String transport = device.transport() == ObcDevice.Transport.NETWORK ? "Network" : "Bluetooth";
		return Component.literal(device.name() + "  [" + transport + "]");
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
				this.minecraft.player.sendOverlayMessage(Component.translatable("voxelvelo.obc.manual_invalid"));
			}
		}
	}

	private void forget() {
		this.ctx.obc.disconnect();
		this.ctx.config.obcPreferredKey = "";
		this.ctx.config.obcPreferredName = "";
		this.ctx.config.save();
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
		String error = this.ctx.obc.mdns().error();
		if (!error.isEmpty() && this.ctx.config.obcNetworkEnabled()) {
			graphics.centeredText(this.font, Component.translatable("voxelvelo.obc.network_unavailable", error), cx, 48, 0xFFFF8080);
		} else {
			graphics.centeredText(this.font, Component.translatable("voxelvelo.obc.hint"), cx, 48, 0xFF909090);
		}
	}

	@Override
	public void onClose() {
		this.ctx.config.save();
		this.ctx.obc.stopDiscovery();
		this.minecraft.gui.setScreen(this.parent);
	}
}
