package dev.michidk.voxelvelo.client.ftms;

import dev.michidk.voxelvelo.VoxelVelo;
import dev.michidk.voxelvelo.client.bluetooth.BluetoothConnection;
import dev.michidk.voxelvelo.client.bluetooth.BluetoothDevice;
import dev.michidk.voxelvelo.client.bluetooth.BluetoothManager;
import dev.michidk.voxelvelo.client.config.VoxelVeloConfig;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;

/**
 * Connects to one FTMS trainer and exposes its latest TrainerTelemetry. Never blocks the caller:
 * every Bluetooth step runs on backend threads and publishes its result through volatile fields.
 *
 * <p>Reconnecting is automatic (with growing delays) while a preferred trainer is configured, unless the
 * player disconnected deliberately.
 */
public final class FtmsClient {
	private static final long FIND_TIMEOUT_MILLIS = 15_000;
	private static final long MIN_RETRY_MILLIS = 4_000;
	private static final long MAX_RETRY_MILLIS = 30_000;

	private final BluetoothManager bluetooth;

	private volatile TrainerState state = TrainerState.DISCONNECTED;
	private volatile TrainerTelemetry telemetry;
	private volatile String deviceName = "";
	private volatile String message = "";
	private volatile BluetoothConnection connection;
	private volatile FtmsControl control;
	/** Incremented on every connect/disconnect so late callbacks from an older attempt are ignored. */
	private volatile int generation;

	private boolean userDisconnected;
	private long nextAttemptAt;
	private long retryDelay = MIN_RETRY_MILLIS;

	public FtmsClient(BluetoothManager bluetooth) {
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

	/** Short status for the UI, e.g. an error from the last attempt. May be empty. */
	public String message() {
		return this.message;
	}

	/** Resistance control of the connected trainer, or null when not connected. */
	public FtmsControl control() {
		return this.control;
	}

	/** Latest telemetry, or null if the trainer is not connected or has gone silent. */
	public @Nullable TrainerTelemetry freshTelemetry() {
		TrainerTelemetry current = this.telemetry;
		return this.isConnected() && current != null && current.isFresh(System.currentTimeMillis()) ? current : null;
	}

	/** Connects on the player request (UI). Clears any earlier deliberate disconnect. */
	public void connect(BluetoothDevice device) {
		this.userDisconnected = false;
		this.retryDelay = MIN_RETRY_MILLIS;
		this.start(device.id(), device.displayName());
	}

	/** Disconnects on the player request and suppresses auto-reconnect until the next manual connect. */
	public void disconnect() {
		this.userDisconnected = true;
		this.teardown("");
	}

	/** Called every client tick while a world is loaded. Starts auto-connect and reconnect attempts. */
	public void tick(VoxelVeloConfig config) {
		if (this.state != TrainerState.DISCONNECTED || this.userDisconnected || !config.autoConnectTrainer || !config.hasPreferredTrainer()) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now < this.nextAttemptAt) {
			return;
		}
		this.nextAttemptAt = now + this.retryDelay;
		this.retryDelay = Math.min(this.retryDelay * 2, MAX_RETRY_MILLIS);
		this.start(config.preferredTrainerId, config.preferredTrainerName);
	}

	private void start(String deviceId, String name) {
		this.teardown("");
		int gen = this.generation;
		this.state = TrainerState.CONNECTING;
		this.deviceName = name;
		this.message = "Searching for trainer...";

		if (!this.bluetooth.isAvailable()) {
			this.fail(gen, this.bluetooth.unavailableReason());
			return;
		}
		this.bluetooth.find(deviceId, FIND_TIMEOUT_MILLIS)
			.thenCompose(device -> {
				this.bluetooth.releaseScan();
				if (gen == this.generation) {
					this.message = "Connecting...";
				}
				return this.bluetooth.backend().connect(device.id(), () -> this.onLinkLost(gen));
			})
			.thenCompose(conn -> this.setUp(gen, conn))
			.whenComplete((ignored, error) -> {
				if (error != null) {
					this.fail(gen, describe(error));
				}
			});
	}

	private CompletableFuture<Void> setUp(int gen, BluetoothConnection conn) {
		if (gen != this.generation) {
			conn.disconnect();
			return CompletableFuture.completedFuture(null);
		}
		if (!conn.serviceUuids().contains(Ftms.SERVICE)) {
			conn.disconnect();
			throw new IllegalStateException("Device is not an FTMS fitness machine");
		}
		this.connection = conn;
		FtmsControl newControl = new FtmsControl(conn);
		this.control = newControl;
		return conn.subscribe(Ftms.SERVICE, Ftms.INDOOR_BIKE_DATA, data -> this.onBikeData(gen, data)).thenRun(() -> {
			if (gen != this.generation) {
				return;
			}
			this.state = TrainerState.CONNECTED;
			this.message = "";
			this.retryDelay = MIN_RETRY_MILLIS;
			VoxelVelo.LOGGER.info("Connected to trainer {}", this.deviceName);
			newControl.initialise();
		});
	}

	private void onBikeData(int gen, byte[] data) {
		if (gen != this.generation) {
			return;
		}
		IndoorBikeData parsed = IndoorBikeData.parse(data);
		if (parsed != null) {
			this.telemetry = TrainerTelemetry.merge(this.telemetry, parsed, System.currentTimeMillis());
		}
	}

	private void onLinkLost(int gen) {
		if (gen == this.generation) {
			VoxelVelo.LOGGER.info("Trainer {} disconnected", this.deviceName);
			this.teardown("Trainer disconnected");
		}
	}

	private void fail(int gen, String reason) {
		if (gen != this.generation) {
			return;
		}
		VoxelVelo.LOGGER.info("Trainer connection failed: {}", reason);
		this.teardown(reason);
	}

	/** Drops the current link and resets to DISCONNECTED. Trainer power becomes zero at once. */
	private void teardown(String reason) {
		this.generation++;
		BluetoothConnection old = this.connection;
		this.connection = null;
		this.control = null;
		this.telemetry = null;
		this.state = TrainerState.DISCONNECTED;
		this.message = reason;
		if (old != null) {
			try {
				old.disconnect();
			} catch (Throwable t) {
				VoxelVelo.LOGGER.debug("Error while disconnecting trainer", t);
			}
		}
	}

	private static String describe(Throwable error) {
		Throwable cause = error;
		while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
			cause = cause.getCause();
		}
		if (cause instanceof TimeoutException) {
			return "Trainer not found nearby";
		}
		String text = cause.getMessage();
		return text == null || text.isBlank() ? cause.getClass().getSimpleName() : text;
	}
}
