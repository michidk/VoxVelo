package dev.michidk.voxvelo.client.ui;

import dev.michidk.voxelfitness.client.FitnessRuntime;
import dev.michidk.voxelfitness.client.ui.Tips;
import dev.michidk.voxvelo.client.bluetooth.BleServiceClient;
import dev.michidk.voxvelo.client.bluetooth.BluetoothDevice;
import dev.michidk.voxvelo.client.bluetooth.ConnectionState;
import dev.michidk.voxvelo.client.fitness.FitnessConfig;
import dev.michidk.voxvelo.client.fitness.FitnessStatus;
import dev.michidk.voxvelo.client.ftms.Ftms;
import dev.michidk.voxvelo.client.heartrate.HeartRate;
import dev.michidk.voxvelo.client.powermeter.CyclingPower;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Every Bluetooth connection in one place: the trainer, a separate power meter and a heart rate sensor. One scan lists
 * all nearby devices that can fill one of these roles, and each device offers a button per role it supports.
 * Everything here is non-blocking.
 */
public class BluetoothScreen extends Screen {
	private static final int MAX_ROWS = 6;
	private static final int WIDTH = 310;
	private static final int ROLES_TOP = 48;
	private static final int ROW_HEIGHT = 22;
	/** A role's status text fills its row up to the Disconnect and Forget buttons, which only exist when they can do something. */
	private static final int DISCONNECT_WIDTH = 68;
	private static final int FORGET_WIDTH = 54;
	private static final int ROLE_BUTTON_WIDTH = 56;
	private static final int ROLE_COLUMN = ROLE_BUTTON_WIDTH + 2;
	private static final int NAME_WIDTH = WIDTH - Role.values().length * ROLE_COLUMN;
	private static final long SCAN_MILLIS = 20_000;

	/** Stores the device chosen for a role; empty strings forget it. */
	@FunctionalInterface
	private interface Saver {
		void save(FitnessConfig config, String id, String name);
	}

	/** A Bluetooth role: the service it needs, the client that fills it, and where its device is saved. */
	private enum Role {
		TRAINER("trainer", Ftms.SERVICE, ctx -> ctx.ftms, FitnessStatus::trainerStatus,
			config -> config.preferredTrainerId, config -> config.preferredTrainerName,
			(config, id, name) -> {
				config.preferredTrainerId = id;
				config.preferredTrainerName = name;
			}),
		POWER_METER("power_meter", CyclingPower.SERVICE, ctx -> ctx.powerMeter, FitnessStatus::powerMeterStatus,
			config -> config.preferredPowerMeterId, config -> config.preferredPowerMeterName,
			(config, id, name) -> {
				config.preferredPowerMeterId = id;
				config.preferredPowerMeterName = name;
			}),
		HEART_RATE("heart_rate", HeartRate.SERVICE, ctx -> ctx.heartRate, FitnessStatus::heartRateStatus,
			config -> config.preferredHeartRateId, config -> config.preferredHeartRateName,
			(config, id, name) -> {
				config.preferredHeartRateId = id;
				config.preferredHeartRateName = name;
			});

		final String key;
		final String service;
		final Function<FitnessRuntime, BleServiceClient> client;
		final Function<FitnessRuntime, Component> status;
		final Function<FitnessConfig, String> savedId;
		final Function<FitnessConfig, String> savedName;
		final Saver saver;

		Role(String key, String service, Function<FitnessRuntime, BleServiceClient> client, Function<FitnessRuntime, Component> status,
			Function<FitnessConfig, String> savedId, Function<FitnessConfig, String> savedName, Saver saver) {
			this.key = key;
			this.service = service;
			this.client = client;
			this.status = status;
			this.savedId = savedId;
			this.savedName = savedName;
			this.saver = saver;
		}

		Component label() {
			return Component.translatable("voxvelo.bluetooth.role." + this.key);
		}
	}

	/** A nearby device with the roles it can be connected as right now. */
	private record Row(BluetoothDevice device, List<Role> roles) {}

	private final Screen parent;
	private final FitnessRuntime ctx = FitnessRuntime.get();
	private String signature = "";
	private List<Row> shown = List.of();
	private int hidden;
	private int listTop;

	public BluetoothScreen(Screen parent) {
		super(Component.translatable("voxvelo.bluetooth.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - WIDTH / 2;
		int y = ROLES_TOP;

		for (Role role : Role.values()) {
			int edge = left + WIDTH;
			if (this.canForget(role)) {
				edge -= FORGET_WIDTH;
				this.addRenderableWidget(Button.builder(Component.translatable("voxvelo.trainer.forget"), button -> this.forget(role))
					.tooltip(Tips.of("voxvelo.trainer.forget")).bounds(edge, y, FORGET_WIDTH, 20).build());
				edge -= 2;
			}
			if (this.canDisconnect(role)) {
				edge -= DISCONNECT_WIDTH;
				this.addRenderableWidget(Button.builder(Component.translatable("voxvelo.trainer.disconnect"), button -> this.client(role).disconnect())
					.tooltip(Tips.of("voxvelo.trainer.disconnect")).bounds(edge, y, DISCONNECT_WIDTH, 20).build());
			} else if (this.canConnect(role)) {
				edge -= DISCONNECT_WIDTH;
				this.addRenderableWidget(Button.builder(Component.translatable("voxvelo.bluetooth.connect"), button -> this.connectSaved(role))
					.tooltip(Tips.of("voxvelo.bluetooth.connect")).bounds(edge, y, DISCONNECT_WIDTH, 20).build());
			}
			y += ROW_HEIGHT;
		}
		y += 4;

		this.addRenderableWidget(Button.builder(this.scanLabel(), button -> this.toggleScan())
			.tooltip(Tips.of("voxvelo.bluetooth.scan")).bounds(left, y, WIDTH, 20).build());
		y += ROW_HEIGHT + 2;

		this.listTop = y;
		List<Row> rows = this.rows();
		int visible = Math.max(0, Math.min(MAX_ROWS, (this.height - 58 - y + 2) / ROW_HEIGHT));
		this.shown = rows.subList(0, Math.min(visible, rows.size()));
		this.hidden = rows.size() - this.shown.size();
		for (Row row : this.shown) {
			for (Role role : row.roles()) {
				int x = left + WIDTH - (Role.values().length - role.ordinal()) * ROLE_COLUMN + 2;
				this.addRenderableWidget(Button.builder(role.label(), button -> this.choose(role, row.device()))
					.tooltip(Tips.of("voxvelo.bluetooth.role." + role.key)).bounds(x, y, ROLE_BUTTON_WIDTH, 20).build());
			}
			y += ROW_HEIGHT;
		}

		int half = (WIDTH - 10) / 2;
		int by = this.height - 54;
		FitnessConfig config = this.ctx.config;
		boolean autoAll = config.autoConnectTrainer && config.autoConnectPowerMeter && config.autoConnectHeartRate;
		this.addRenderableWidget(CycleButton.onOffBuilder(autoAll)
			.withTooltip(value -> Tips.of("voxvelo.config.auto_connect"))
			.create(left, by, half, 20, Component.translatable("voxvelo.config.auto_connect"), (button, value) -> {
				config.autoConnectTrainer = value;
				config.autoConnectPowerMeter = value;
				config.autoConnectHeartRate = value;
			}));
		this.addRenderableWidget(Button.builder(Component.translatable("voxvelo.bluetooth.trainer_settings"),
				button -> this.minecraft.gui.setScreen(new TrainerSettingsScreen(this)))
			.tooltip(Tips.of("voxvelo.bluetooth.trainer_settings")).bounds(left + half + 10, by, half, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
			.bounds(left, this.height - 30, WIDTH, 20).build());
		this.signature = this.currentSignature();
	}

	private Component scanLabel() {
		return Component.translatable(this.ctx.bluetooth.isScanning() ? "voxvelo.bluetooth.scanning" : "voxvelo.bluetooth.scan");
	}

	/** Nearby devices that advertise a role's service. One device can fill several roles, such as a trainer that also reports power. */
	private List<Row> rows() {
		List<String> services = Arrays.stream(Role.values()).map(role -> role.service).toList();
		List<Row> rows = new ArrayList<>();
		for (BluetoothDevice device : this.ctx.bluetooth.devicesAdvertisingAny(services)) {
			List<Role> roles = new ArrayList<>();
			for (Role role : Role.values()) {
				if (device.advertises(role.service)) {
					roles.add(role);
				}
			}
			if (!roles.isEmpty()) {
				rows.add(new Row(device, roles));
			}
		}
		return rows;
	}

	private BleServiceClient client(Role role) {
		return role.client.apply(this.ctx);
	}

	private String savedId(Role role) {
		return role.savedId.apply(this.ctx.config);
	}

	/** Disconnecting only means something while a connection exists or is being made. */
	private boolean canDisconnect(Role role) {
		return this.client(role).state() != ConnectionState.DISCONNECTED;
	}

	/** A saved device that is not connected can be connected again without scanning for it first. */
	private boolean canConnect(Role role) {
		return this.canForget(role) && this.client(role).state() == ConnectionState.DISCONNECTED;
	}

	private void connectSaved(Role role) {
		// Connecting looks the device up by its id (scanning for it if it has not been seen yet); only the name is used besides.
		this.client(role).connect(new BluetoothDevice(this.savedId(role), role.savedName.apply(this.ctx.config), 0, Set.of()));
	}

	/** A saved device can be forgotten whether or not it is connected right now; with none saved there is nothing to forget. */
	private boolean canForget(Role role) {
		return !this.savedId(role).isEmpty();
	}

	/** Width left for a role's status text once the buttons it has are placed. */
	private int statusWidth(Role role) {
		int width = WIDTH;
		if (this.canForget(role)) {
			width -= FORGET_WIDTH + 2;
		}
		if (this.canDisconnect(role) || this.canConnect(role)) {
			width -= DISCONNECT_WIDTH + 2;
		}
		return width - 4;
	}

	/** Why the last attempt to connect a role failed, shown in full because the row's status text is cut off. Null when nothing failed. */
	private @Nullable Component failure() {
		for (Role role : Role.values()) {
			BleServiceClient client = this.client(role);
			Component message = client.message();
			if (client.state() == ConnectionState.DISCONNECTED && message != null) {
				return Component.translatable("voxvelo.bluetooth.failure", role.label(), message);
			}
		}
		return null;
	}

	private void toggleScan() {
		if (this.ctx.bluetooth.isScanning()) {
			this.ctx.bluetooth.stopScan();
		} else {
			this.ctx.bluetooth.scan(SCAN_MILLIS);
		}
	}

	private void choose(Role role, BluetoothDevice device) {
		role.saver.save(this.ctx.config, device.id(), device.displayName());
		this.ctx.config.save();
		this.ctx.bluetooth.stopScan();
		this.client(role).connect(device);
	}

	private void forget(Role role) {
		this.client(role).disconnect();
		role.saver.save(this.ctx.config, "", "");
		this.ctx.config.save();
	}

	private String currentSignature() {
		StringBuilder sb = new StringBuilder(this.ctx.bluetooth.isScanning() ? "S" : "-");
		for (Role role : Role.values()) {
			sb.append('/').append(this.savedId(role)).append(this.client(role).state() == ConnectionState.DISCONNECTED ? 'x' : 'c');
		}
		List<Row> rows = this.rows();
		for (int i = 0; i < Math.min(MAX_ROWS, rows.size()); i++) {
			Row row = rows.get(i);
			sb.append('|').append(row.device().id()).append(row.device().name()).append(row.device().rssi() / 5).append(row.roles());
		}
		return sb.append('#').append(rows.size()).toString();
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
		int left = this.width / 2 - WIDTH / 2;
		int cx = this.width / 2;
		graphics.centeredText(this.font, this.title, cx, 20, 0xFFFFFFFF);
		Component failure = this.failure();
		if (!this.ctx.bluetooth.isAvailable()) {
			graphics.centeredText(this.font, Component.translatable("voxvelo.bluetooth.unavailable", this.ctx.bluetooth.unavailableReason()), cx, 36, 0xFFFF8080);
		} else if (failure != null) {
			graphics.centeredText(this.font, Component.literal(this.fit(failure.getString(), WIDTH + 40)), cx, 36, 0xFFFF8080);
		} else {
			graphics.centeredText(this.font, Component.translatable("voxvelo.bluetooth.hint"), cx, 36, 0xFF909090);
		}

		int y = ROLES_TOP;
		for (Role role : Role.values()) {
			graphics.text(this.font, this.fit(role.status.apply(this.ctx).getString(), this.statusWidth(role)), left, y + 6, 0xFFC0C0C0);
			y += ROW_HEIGHT;
		}

		y = this.listTop;
		for (Row row : this.shown) {
			BluetoothDevice device = row.device();
			String rssi = String.valueOf(device.rssi());
			graphics.text(this.font, this.fit(device.displayName(), NAME_WIDTH - this.font.width(rssi) - 10), left, y + 6, 0xFFFFFFFF);
			graphics.text(this.font, rssi, left + NAME_WIDTH - 4 - this.font.width(rssi), y + 6, 0xFF808080);
			y += ROW_HEIGHT;
		}
		if (this.hidden > 0) {
			graphics.text(this.font, Component.translatable("voxvelo.bluetooth.more", this.hidden), left, y + 6, 0xFF909090);
		}
	}

	/** Shortens text with an ellipsis so it never runs into the buttons next to it. */
	private String fit(String text, int maxWidth) {
		if (this.font.width(text) <= maxWidth) {
			return text;
		}
		return this.font.plainSubstrByWidth(text, maxWidth - this.font.width("...")) + "...";
	}

	@Override
	public void onClose() {
		this.ctx.config.save();
		this.ctx.bluetooth.stopScan();
		this.minecraft.gui.setScreen(this.parent);
	}
}
