package dev.michidk.voxvelo.fitnesslib.client.ui;

import dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime;
import dev.michidk.voxvelo.fitnesslib.client.bluetooth.ConnectionState;
import dev.michidk.voxvelo.fitnesslib.client.obc.ObcDevice;
import dev.michidk.voxvelo.fitnesslib.client.obc.ObcMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Picks the OpenBikeControl device (for example BikeControl on a phone) and the transports to use. */
public class ObcScreen extends SettingsScreen {
	private static final int WIDTH = SettingsList.WIDTH;

	private final FitnessRuntime ctx = FitnessRuntime.get();
	private final Set<String> hidden = new HashSet<>();

	private String manualText = "";
	/** Why the typed address was refused, shown under it until it is edited. */
	private @Nullable Component manualError;
	private String signature = "";

	public ObcScreen(Screen parent) {
		super(parent, Component.translatable("voxvelo_fitness_lib.obc.title"));
		this.ctx.obc.startDiscovery(this.ctx.config);
	}

	@Override
	protected void addOptions() {

		this.rows.addText(this::hint, HINT);
		this.rows.addRow(new ValueSlider(0, 0, WIDTH, 0.1, 3.0, 0.1, this.ctx.config.steeringSensitivity,
			value -> Component.translatable("voxvelo_fitness_lib.obc.steering_sensitivity", String.format(java.util.Locale.ROOT, "%.1f", value)),
			value -> this.ctx.config.steeringSensitivity = value).tip("voxvelo_fitness_lib.obc.steering_sensitivity"));

		this.rows.addRow(CycleButton.builder(ObcMode::label, this.ctx.config.obcMode)
			.withValues(ObcMode.values())
			.withTooltip(mode -> Tips.of("voxvelo_fitness_lib.obc.mode"))
			.create(0, 0, WIDTH, 20, Component.translatable("voxvelo_fitness_lib.obc.mode"), (button, value) -> {
				this.ctx.config.obcMode = value;
				this.ctx.obc.rescan(this.ctx.config);
			}));

		this.section("saved");
		String savedKey = this.ctx.config.obcPreferredKey;
		List<ObcDevice> discovered = this.ctx.obc.discovered(this.ctx.config);
		if (savedKey.isEmpty()) {
			this.rows.addText(() -> label("saved_empty"), HINT);
		} else {
			this.deviceCard(savedKey, this.ctx.config.obcPreferredName, savedKey.startsWith("ble:") ? ObcMode.BLUETOOTH : ObcMode.NETWORK,
				discovered.stream().filter(device -> device.key().equals(savedKey)).findFirst().orElse(null), true);
		}
		this.rows.addRow(Button.builder(label("rescan"), button -> {
			this.hidden.clear();
			this.ctx.obc.rescan(this.ctx.config);
		}).tooltip(Tips.of("voxvelo_fitness_lib.obc.rescan")).bounds(0, 0, WIDTH, 20).build());
		this.section("nearby");
		List<ObcDevice> devices = discovered.stream().filter(device -> !device.key().equals(savedKey) && !this.hidden.contains(device.key())).toList();
		if (devices.isEmpty()) this.rows.addText(() -> label(this.ctx.obc.searching(this.ctx.config) ? "searching" : "none_found"), HINT);
		for (ObcDevice device : devices) {
			this.deviceCard(device.key(), device.name(), device.transport() == ObcDevice.Transport.NETWORK ? ObcMode.NETWORK : ObcMode.BLUETOOTH, device, false);
		}
		this.section("manual");
		EditBox manual = new EditBox(this.font, 0, 0, 200, 20, Component.translatable("voxvelo_fitness_lib.obc.manual"));
		manual.setTooltip(Tips.of("voxvelo_fitness_lib.obc.manual"));
		manual.setHint(Component.translatable("voxvelo_fitness_lib.obc.manual_hint"));
		manual.setMaxLength(64);
		manual.setValue(this.manualText);
		manual.setResponder(text -> {
			this.manualText = text;
			this.manualError = null;
		});
		this.rows.addRow(manual, Button.builder(Component.translatable("voxvelo_fitness_lib.obc.connect"), button -> this.connectManual())
			.tooltip(Tips.of("voxvelo_fitness_lib.obc.connect"))
			.bounds(210, 0, 100, 20).build());
		this.rows.addText(() -> this.manualError, ERROR);
		this.rows.addRow(CycleButton.onOffBuilder(this.ctx.config.obcAutoConnect)
			.withTooltip(value -> Tips.of("voxvelo_fitness_lib.config.auto_connect"))
			.create(0, 0, WIDTH, 20, Component.translatable("voxvelo_fitness_lib.config.auto_connect"),
				(button, value) -> this.ctx.config.obcAutoConnect = value));
		this.signature = this.currentSignature();
	}

	/** Why network discovery cannot work, or else how this page works. */
	private Component hint() {
		if (this.ctx.obc.mdns().failed() && this.ctx.config.obcMode.network()) {
			return Component.translatable("voxvelo_fitness_lib.obc.network_unavailable").withColor(ERROR & 0xFFFFFF);
		}
		return Component.translatable("voxvelo_fitness_lib.obc.hint");
	}

	private static Component label(String key) {
		return Component.translatable("voxvelo_fitness_lib.obc." + key);
	}

	private void section(String key) {
		this.rows.addText((graphics, font, left, top) -> {
			graphics.text(font, label(key), left + 4, top + 2, STATUS);
			graphics.fill(left + font.width(label(key)) + 12, top + 6, left + WIDTH - 4, top + 7, 0xFF52606B);
		});
	}

	/** A compact card with actions beside the name, status below, and a transport badge. */
	private void deviceCard(String key, String name, ObcMode transport, @Nullable ObcDevice device, boolean saved) {
		boolean connected = saved && this.ctx.obc.isConnected();
		boolean open = saved && this.ctx.obc.state() != ConnectionState.DISCONNECTED;
		java.util.ArrayList<net.minecraft.client.gui.components.AbstractWidget> widgets = new java.util.ArrayList<>();
		Button connect = Button.builder(open ? Component.translatable("voxvelo_fitness_lib.trainer.disconnect") : label("connect"), button -> {
			if (open) this.ctx.obc.disconnect();
			else if (device != null) this.choose(device);
			else if (key.startsWith("ble:")) this.choose(ObcDevice.bluetooth(key.substring(4), name));
			else if (key.startsWith("tcp:")) {
				this.manualText = key.substring(4);
				this.connectManual();
			}
		}).bounds(saved ? WIDTH - 124 : WIDTH - 68, 0, 68, 20).build();
		connect.active = open || ((device != null || key.startsWith("tcp:") || key.startsWith("ble:")) && this.ctx.config.obcMode != ObcMode.OFF);
		widgets.add(connect);
		if (saved) widgets.add(Button.builder(Component.translatable("voxvelo_fitness_lib.trainer.forget"), button -> this.forget())
			.tooltip(Tips.of("voxvelo_fitness_lib.trainer.forget")).bounds(WIDTH - 54, 0, 54, 20).build());
		int textWidth = saved ? WIDTH - 138 : WIDTH - 82;
		Component message = saved ? this.ctx.obc.message() : null;
		Component status = message != null ? message : saved ? Component.translatable("voxvelo_fitness_lib.connection.state." +
			this.ctx.obc.state().name().toLowerCase(java.util.Locale.ROOT)) : transport.label();
		Button details = new Button(5, 0, textWidth, 24, Component.literal(name), button -> {}, supplier -> supplier.get()) {
			@Override
			protected void extractContents(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
				// Invisible hit area keeps long names and errors available to keyboard users and on hover.
			}
		};
		details.setTooltip(Tooltip.create(Component.literal(name).append("\n").append(status)));
		widgets.add(details);
		this.rows.addRow(widgets, (graphics, font, left, top) -> {
			graphics.fill(left, top, left + WIDTH, top + 25, 0xB0182028);
			graphics.text(font, this.fit(name, textWidth), left + 7, top + 2, connected || !saved ? 0xFFFFFFFF : HINT);
			graphics.text(font, this.fit(status.getString(), textWidth), left + 7, top + 13, connected && message == null ? 0xFF8EE3B0 : HINT);
		});
		this.rows.addRow(List.of(), (graphics, font, left, top) -> {
			graphics.fill(left, top, left + WIDTH, top + 21, 0xB0182028);
			int color = saved && !connected ? HINT : transport == ObcMode.NETWORK ? 0xFF8EE3B0 : 0xFF80BFFF;
			int width = font.width(transport.label()) + 12;
			graphics.fill(left + 6, top, left + 6 + width, top + 17, color);
			graphics.fill(left + 7, top + 1, left + 5 + width, top + 16, 0xFF202A33);
			graphics.text(font, transport.label(), left + 12, top + 4, color);
		});
	}

	private String fit(String text, int width) {
		return this.font.width(text) <= width ? text : this.font.plainSubstrByWidth(text, width - this.font.width("...")) + "...";
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
			this.manualError = Component.translatable("voxvelo_fitness_lib.obc.manual_invalid");
		}
	}

	private void forget() {
		this.hidden.add(this.ctx.config.obcPreferredKey);
		this.ctx.obc.disconnect();
		this.ctx.config.obcPreferredKey = "";
		this.ctx.config.obcPreferredName = "";
		this.ctx.config.save();
	}

	private String currentSignature() {
		StringBuilder sb = new StringBuilder(this.ctx.config.obcMode.name()).append(this.ctx.config.obcPreferredKey).append(this.ctx.obc.state()).append(this.ctx.obc.message()).append(this.hidden);
		for (ObcDevice device : this.ctx.obc.discovered(this.ctx.config)) {
			sb.append('|').append(device.key());
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
	protected void save() {
		this.ctx.config.save();
	}

	@Override
	public void onClose() {
		this.ctx.obc.stopDiscovery();
		super.onClose();
	}
}
