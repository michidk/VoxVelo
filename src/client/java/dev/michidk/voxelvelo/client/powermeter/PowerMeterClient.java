package dev.michidk.voxelvelo.client.powermeter;

import dev.michidk.voxelvelo.VoxelVelo;
import dev.michidk.voxelvelo.client.bluetooth.BluetoothConnection;
import dev.michidk.voxelvelo.client.bluetooth.BluetoothDevice;
import dev.michidk.voxelvelo.client.bluetooth.BluetoothManager;
import dev.michidk.voxelvelo.client.fitness.FitnessConfig;
import dev.michidk.voxelvelo.client.ftms.TrainerState;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;

/** Maintains an independent BLE Cycling Power Service connection and its latest watt reading. */
public final class PowerMeterClient {
	private static final long FIND_TIMEOUT_MILLIS = 15_000;
	private static final long MIN_RETRY_MILLIS = 4_000;
	private static final long MAX_RETRY_MILLIS = 30_000;

	private final BluetoothManager bluetooth;
	private volatile TrainerState state = TrainerState.DISCONNECTED;
	private volatile PowerMeterReading reading;
	private volatile String deviceName = "";
	private volatile String message = "";
	private volatile BluetoothConnection connection;
	private volatile int generation;
	private boolean userDisconnected;
	private long nextAttemptAt;
	private long retryDelay = MIN_RETRY_MILLIS;

	public PowerMeterClient(BluetoothManager bluetooth) {
		this.bluetooth = bluetooth;
	}

	public TrainerState state() {
		return this.state;
	}

	public boolean isConnected() {
		return this.state == TrainerState.CONNECTED;
	}

	public String deviceName() {
		return this.deviceName;
	}

	public String message() {
		return this.message;
	}

	public @Nullable PowerMeterReading freshReading() {
		PowerMeterReading current = this.reading;
		return this.isConnected() && current != null && current.isFresh(System.currentTimeMillis()) ? current : null;
	}

	public void connect(BluetoothDevice device) {
		this.userDisconnected = false;
		this.retryDelay = MIN_RETRY_MILLIS;
		this.start(device.id(), device.displayName());
	}

	public void disconnect() {
		this.userDisconnected = true;
		this.teardown("");
	}

	public void tick(FitnessConfig config) {
		if (this.state != TrainerState.DISCONNECTED || this.userDisconnected
				|| !config.autoConnectPowerMeter || !config.hasPreferredPowerMeter()) return;
		long now = System.currentTimeMillis();
		if (now < this.nextAttemptAt) return;
		this.nextAttemptAt = now + this.retryDelay;
		this.retryDelay = Math.min(this.retryDelay * 2, MAX_RETRY_MILLIS);
		this.start(config.preferredPowerMeterId, config.preferredPowerMeterName);
	}

	private void start(String deviceId, String name) {
		this.teardown("");
		int gen = this.generation;
		this.state = TrainerState.CONNECTING;
		this.deviceName = name;
		this.message = "Searching for power meter...";
		if (!this.bluetooth.isAvailable()) {
			this.fail(gen, this.bluetooth.unavailableReason());
			return;
		}
		this.bluetooth.find(deviceId, FIND_TIMEOUT_MILLIS)
			.thenCompose(device -> {
				this.bluetooth.releaseScan();
				if (gen == this.generation) this.message = "Connecting...";
				return this.bluetooth.backend().connect(device.id(), () -> this.onLinkLost(gen));
			})
			.thenCompose(conn -> this.setUp(gen, conn))
			.whenComplete((ignored, error) -> {
				if (error != null) this.fail(gen, describe(error));
			});
	}

	private CompletableFuture<Void> setUp(int gen, BluetoothConnection conn) {
		if (gen != this.generation) {
			conn.disconnect();
			return CompletableFuture.completedFuture(null);
		}
		if (!conn.serviceUuids().contains(CyclingPower.SERVICE)) {
			conn.disconnect();
			throw new IllegalStateException("Device does not provide Cycling Power Service");
		}
		this.connection = conn;
		return conn.subscribe(CyclingPower.SERVICE, CyclingPower.MEASUREMENT, data -> this.onMeasurement(gen, data)).thenRun(() -> {
			if (gen != this.generation) return;
			this.state = TrainerState.CONNECTED;
			this.message = "";
			this.retryDelay = MIN_RETRY_MILLIS;
			VoxelVelo.LOGGER.info("Connected to power meter {}", this.deviceName);
		});
	}

	private void onMeasurement(int gen, byte[] data) {
		if (gen != this.generation) return;
		CyclingPowerMeasurement parsed = CyclingPowerMeasurement.parse(data);
		if (parsed != null) this.reading = PowerMeterReading.from(parsed, System.currentTimeMillis());
	}

	private void onLinkLost(int gen) {
		if (gen == this.generation) {
			VoxelVelo.LOGGER.info("Power meter {} disconnected", this.deviceName);
			this.teardown("Power meter disconnected");
		}
	}

	private void fail(int gen, String reason) {
		if (gen != this.generation) return;
		VoxelVelo.LOGGER.info("Power meter connection failed: {}", reason);
		this.teardown(reason);
	}

	private void teardown(String reason) {
		this.generation++;
		BluetoothConnection old = this.connection;
		this.connection = null;
		this.reading = null;
		this.state = TrainerState.DISCONNECTED;
		this.message = reason;
		if (old != null) {
			try {
				old.disconnect();
			} catch (Throwable t) {
				VoxelVelo.LOGGER.debug("Error while disconnecting power meter", t);
			}
		}
	}

	private static String describe(Throwable error) {
		Throwable cause = error;
		while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
			cause = cause.getCause();
		}
		if (cause instanceof TimeoutException) return "Power meter not found nearby";
		String text = cause.getMessage();
		return text == null || text.isBlank() ? cause.getClass().getSimpleName() : text;
	}
}
