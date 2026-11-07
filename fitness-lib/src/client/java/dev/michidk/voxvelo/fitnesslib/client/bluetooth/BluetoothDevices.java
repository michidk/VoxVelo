package dev.michidk.voxvelo.fitnesslib.client.bluetooth;

import dev.michidk.voxvelo.fitnesslib.client.fitness.FitnessConfig;
import dev.michidk.voxvelo.fitnesslib.client.ftms.Ftms;
import dev.michidk.voxvelo.fitnesslib.client.heartrate.HeartRate;
import dev.michidk.voxvelo.fitnesslib.client.powermeter.CyclingPower;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.network.chat.Component;

/** Keeps device connections independent of the exclusive capability sources selected for riding. */
public final class BluetoothDevices {
	public enum Capability {
		TRAINER("trainer", Ftms.SERVICE, 0xFF80BFFF),
		POWER("power_meter", CyclingPower.SERVICE, 0xFFFFD878),
		HEART("heart_rate", HeartRate.SERVICE, 0xFFFF8C9C);

		public final String key;
		public final String service;
		public final int color;

		Capability(String key, String service, int color) {
			this.key = key;
			this.service = service;
			this.color = color;
		}
	}

	/** A saved peripheral; a connection stays open even while none of its capabilities is selected. */
	public final class Device extends DeviceClient {
		private BluetoothDevice descriptor;
		private boolean assignUnused;
		private boolean confirmed;

		private Device(BluetoothDevice descriptor) {
			super(BluetoothDevices.this.bluetooth, BluetoothDevices.this.gameThread, "Bluetooth device");
			this.descriptor = descriptor;
		}

		public BluetoothDevice descriptor() { return this.descriptor; }

		public List<Capability> capabilities() {
			return Arrays.stream(Capability.values()).filter(c -> this.descriptor.advertises(c.service)).toList();
		}

		public void connect() {
			this.playerRequested();
			this.start();
		}

		private void start() {
			this.pairingDevice(this.descriptor.id());
			int gen = this.begin(this.descriptor.displayName(), Component.translatable("voxvelo_fitness_lib.connection.searching"));
			BluetoothDevices.this.bluetooth.find(this.descriptor.id(), 15_000)
				.thenComposeAsync(found -> {
					BluetoothDevices.this.bluetooth.releaseScan();
					if (!this.isCurrent(gen)) return CompletableFuture.failedFuture(new CancellationException());
					this.descriptor = found.withServicesFrom(this.descriptor);
					this.setMessage(Component.translatable("voxvelo_fitness_lib.connection.connecting"));
					return BluetoothDevices.this.bluetooth.connect(found.id(), this.callback(gen, () -> this.lost(gen,
						Component.translatable("voxvelo_fitness_lib.connection.lost"))));
				}, BluetoothDevices.this.gameThread)
				.whenCompleteAsync((connection, error) -> {
					if (error != null) { this.fail(gen, error); return; }
					if (!this.isCurrent(gen)) { connection.disconnect(); return; }
					this.retainConnection(connection);
					this.descriptor = new BluetoothDevice(this.descriptor.id(), this.descriptor.name(), this.descriptor.rssi(), connection.serviceUuids())
						.withServicesFrom(this.descriptor);
					this.connected(gen);
					this.confirmed = true;
					BluetoothDevices.this.remember();
					for (Capability capability : this.capabilities()) {
						if (this.assignUnused && BluetoothDevices.this.preferredId(capability).isEmpty()) BluetoothDevices.this.select(this, capability);
					}
					this.assignUnused = false;
					BluetoothDevices.this.updateSources();
				}, BluetoothDevices.this.gameThread);
		}
	}

	private final BluetoothManager bluetooth;
	private final Executor gameThread;
	private final FitnessConfig config;
	private final BleServiceClient[] clients;
	private final Runnable save;
	private final Map<String, Device> devices = new LinkedHashMap<>();
	private final Set<String> hidden = new HashSet<>();
	private final Map<Capability, String> activeSources = new java.util.EnumMap<>(Capability.class);

	public BluetoothDevices(BluetoothManager bluetooth, Executor gameThread, FitnessConfig config,
		BleServiceClient trainer, BleServiceClient power, BleServiceClient heart) {
		this(bluetooth, gameThread, config, trainer, power, heart, config::save);
	}

	BluetoothDevices(BluetoothManager bluetooth, Executor gameThread, FitnessConfig config,
		BleServiceClient trainer, BleServiceClient power, BleServiceClient heart, Runnable save) {
		this.bluetooth = bluetooth;
		this.gameThread = gameThread;
		this.config = config;
		this.clients = new BleServiceClient[] {trainer, power, heart};
		this.save = save;
		for (BluetoothDevice saved : config.bluetoothDevices) {
			Device device = new Device(saved);
			device.confirmed = true;
			this.devices.put(saved.id(), device);
		}
		this.migrate(config.preferredTrainerId, config.preferredTrainerName, Capability.TRAINER);
		this.migrate(config.preferredPowerMeterId, config.preferredPowerMeterName, Capability.POWER);
		this.migrate(config.preferredHeartRateId, config.preferredHeartRateName, Capability.HEART);
	}

	private void migrate(String id, String name, Capability capability) {
		if (id.isEmpty()) return;
		BluetoothDevice legacy = new BluetoothDevice(id, name, 0, Set.of(capability.service));
		Device existing = this.devices.get(id);
		if (existing == null) this.devices.put(id, new Device(legacy));
		else existing.descriptor = existing.descriptor.withServicesFrom(legacy);
	}

	public List<Device> saved() { return this.devices.values().stream().filter(device -> device.confirmed).toList(); }

	/** The current connection attempt, including devices not yet successfully connected. */
	public Device attempt(String id) { return this.devices.get(id); }
	public BleServiceClient client(Capability capability) { return this.clients[capability.ordinal()]; }

	public String selectedId(Capability capability) {
		String preferred = this.preferredId(capability);
		if (preferred.isEmpty()) return "";
		Device chosen = this.devices.get(preferred);
		if (chosen != null && chosen.isConnected()) return preferred;
		return this.devices.values().stream().filter(device -> device.isConnected() && device.capabilities().contains(capability))
			.map(device -> device.descriptor.id()).findFirst().orElse(preferred);
	}

	/** The manual choice is retained while another connected sensor temporarily supplies its data. */
	public String preferredId(Capability capability) {
		return switch (capability) {
			case TRAINER -> this.config.preferredTrainerId;
			case POWER -> this.config.preferredPowerMeterId;
			case HEART -> this.config.preferredHeartRateId;
		};
	}

	public boolean selected(Device device, Capability capability) { return device.descriptor.id().equals(this.selectedId(capability)); }

	/** Selecting a source replaces only that capability; other devices keep their physical connections. */
	public void select(Device device, Capability capability) {
		if (!device.capabilities().contains(capability)) return;
		if (!device.isConnected()) {
			this.client(capability).disconnect();
			this.store(capability, device.descriptor.id(), device.descriptor.displayName());
			this.remember();
			device.connect();
			return;
		}
		boolean clear = device.descriptor.id().equals(this.preferredId(capability));
		if (clear && this.client(capability).state() == ConnectionState.DISCONNECTED && this.client(capability).message() != null) {
			this.client(capability).connect(device.descriptor);
			return;
		}
		this.client(capability).disconnect();
		this.store(capability, clear ? "" : device.descriptor.id(), clear ? "" : device.descriptor.displayName());
		this.activeSources.put(capability, clear ? "" : device.descriptor.id());
		if (!clear) this.client(capability).connect(device.descriptor);
		this.remember();
	}

	private void store(Capability capability, String id, String name) {
		switch (capability) {
			case TRAINER -> { this.config.preferredTrainerId = id; this.config.preferredTrainerName = name; }
			case POWER -> { this.config.preferredPowerMeterId = id; this.config.preferredPowerMeterName = name; }
			case HEART -> { this.config.preferredHeartRateId = id; this.config.preferredHeartRateName = name; }
		}
	}

	public void connect(BluetoothDevice descriptor) {
		this.hidden.remove(descriptor.id());
		Device device = this.devices.computeIfAbsent(descriptor.id(), ignored -> {
			Device added = new Device(descriptor);
			added.assignUnused = true;
			return added;
		});
		this.remember();
		device.connect();
	}

	public void disconnect(Device device) {
		for (Capability capability : Capability.values()) if (this.selected(device, capability)) this.client(capability).disconnect();
		device.disconnect();
	}

	/** Forget also hides cached advertisements until the player explicitly starts a new scan. */
	public void forget(Device device) {
		this.disconnect(device);
		for (Capability capability : Capability.values()) if (device.descriptor.id().equals(this.preferredId(capability))) this.store(capability, "", "");
		this.devices.remove(device.descriptor.id());
		this.hidden.add(device.descriptor.id());
		this.remember();
	}

	public List<BluetoothDevice> nearby() {
		return this.bluetooth.devicesAdvertisingAny(Arrays.stream(Capability.values()).map(c -> c.service).toList())
			.stream().filter(d -> (this.attempt(d.id()) == null || !this.attempt(d.id()).confirmed) && !this.hidden.contains(d.id())).toList();
	}

	public void scan() { this.hidden.clear(); this.bluetooth.scan(20_000); }

	public void tick() {
		for (Device device : new ArrayList<>(this.devices.values())) {
			if ((device.confirmed || !device.assignUnused) && device.autoAttemptDue(this.config.autoConnectBluetooth)) device.start();
		}
		this.updateSources();
	}

	private void updateSources() {
		for (Capability capability : Capability.values()) {
			Device device = this.devices.get(this.selectedId(capability));
			String target = device != null && device.isConnected() ? device.descriptor.id() : "";
			String previous = this.activeSources.put(capability, target);
			if (!target.equals(previous)) {
				this.client(capability).disconnect();
				if (!target.isEmpty()) this.client(capability).connect(device.descriptor);
			} else if (!target.isEmpty()) {
				this.client(capability).tickSource(this.config.autoConnectBluetooth, device.descriptor);
			}
		}
	}

	private void remember() {
		this.config.bluetoothDevices = new ArrayList<>(this.saved().stream().map(Device::descriptor).toList());
		this.save.run();
	}
}
