package dev.michidk.voxvelo.fitnesslib.client.bluetooth;

import dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * {@link BluetoothBackend} on top of btleplug (WinRT on Windows, BlueZ on Linux, CoreBluetooth on macOS) through the
 * bundled {@code voxvelo_ble} library. This and its {@link BtleplugConnection} and {@link BtleplugNative} are the only
 * classes that touch native code, and they are only ever loaded on the client.
 *
 * <p>Loading the native library can fail (unsupported platform, no adapter, missing BlueZ); every such failure is turned
 * into {@link #unavailableReason()} instead of an exception.
 */
public final class BtleplugBackend implements BluetoothBackend, BtleplugNative.Events {
	/** How long to wait before looking for an adapter again after finding none (the user may switch Bluetooth on). */
	private static final long INIT_RETRY_MILLIS = 5_000;
	private static final long OPEN_TIMEOUT_MILLIS = 10_000;

	private final ExecutorService control = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "voxvelo-ble-control");
		thread.setDaemon(true);
		return thread;
	});
	private final Map<String, BtleplugConnection> connections = new ConcurrentHashMap<>();
	private final AtomicBoolean initStarted = new AtomicBoolean();

	private volatile BtleplugNative ble;
	private volatile boolean ready;
	private volatile Consumer<BluetoothDevice> onDevice;
	private volatile String unavailableReason = "starting up";
	private volatile boolean scanning;
	private volatile long nextInitAt;

	@Override
	public boolean isAvailable() {
		long now = System.currentTimeMillis();
		if (!this.ready && now >= this.nextInitAt && this.initStarted.compareAndSet(false, true)) {
			this.nextInitAt = now + INIT_RETRY_MILLIS;
			this.control.execute(this::initialise);
		}
		return this.ready;
	}

	@Override
	public String unavailableReason() {
		return this.unavailableReason;
	}

	/** Records a temporary lack of adapter and logs it once per distinct reason. */
	private void noAdapter(String reason) {
		if (!reason.equals(this.unavailableReason)) {
			FitnessRuntime.LOGGER.info("Bluetooth: {}", reason);
		}
		this.unavailableReason = reason;
	}

	/** Runs on the control thread. Leaves initStarted set only after a permanent failure. */
	private void initialise() {
		if (this.ready) {
			return;
		}
		try {
			if (this.ble == null) {
				BtleplugNative loaded = BtleplugNative.load();
				loaded.setEvents(this);
				this.ble = loaded;
			}
		} catch (UnsupportedOperationException e) {
			FitnessRuntime.LOGGER.info("Bluetooth: {}", e.getMessage());
			this.unavailableReason = "not supported on this system";
			return;
		} catch (Throwable t) {
			FitnessRuntime.LOGGER.warn("Bluetooth is unavailable", t);
			this.unavailableReason = "library failed to load";
			return;
		}
		try {
			String adapter = this.ble.open().get(OPEN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
			FitnessRuntime.LOGGER.info("Bluetooth adapter ready: {}", adapter);
			this.unavailableReason = "";
			this.ready = true;
		} catch (Exception e) {
			Throwable cause = e.getCause() != null ? e.getCause() : e;
			this.noAdapter(cause.getMessage() != null ? cause.getMessage() : "no adapter found");
			this.initStarted.set(false);
		}
	}

	@Override
	public void startScan(Consumer<BluetoothDevice> onDevice) {
		this.onDevice = onDevice;
		this.control.execute(() -> {
			if (!this.ready) {
				this.initialise();
			}
			if (!this.ready) {
				return;
			}
			try {
				this.ble.startScan().get(OPEN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
				this.scanning = true;
			} catch (Exception e) {
				FitnessRuntime.LOGGER.warn("Could not start Bluetooth scan", e);
				this.scanning = false;
			}
		});
	}

	@Override
	public void stopScan() {
		this.control.execute(() -> {
			if (this.ready) {
				try {
					this.ble.stopScan().get(OPEN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
				} catch (Exception e) {
					FitnessRuntime.LOGGER.debug("Stopping scan failed", e);
				}
			}
			this.scanning = false;
		});
	}

	@Override
	public boolean isScanning() {
		return this.scanning;
	}

	@Override
	public CompletableFuture<BluetoothConnection> connect(String deviceId, Runnable onDisconnected) {
		BtleplugNative current = this.ble;
		if (!this.ready || current == null) {
			return CompletableFuture.failedFuture(new IllegalStateException(this.unavailableReason));
		}
		BtleplugConnection connection = new BtleplugConnection(current, deviceId, onDisconnected, this.connections);
		// Registered before connecting so no disconnect or early notification can slip past.
		this.connections.put(deviceId, connection);
		return current.connect(deviceId)
			// btleplug has finished service discovery once connect completes, so this list is complete.
			.thenCompose(ignored -> current.services(deviceId))
			.handle((services, error) -> {
				if (error != null) {
					this.connections.remove(deviceId, connection);
					throw error instanceof CompletionException completion ? completion : new CompletionException(error);
				}
				connection.setServices(services);
				return connection;
			});
	}

	@Override
	public void device(BluetoothDevice device) {
		Consumer<BluetoothDevice> listener = this.onDevice;
		if (listener != null && this.scanning) {
			listener.accept(device);
		}
	}

	@Override
	public void disconnected(String deviceId) {
		// Only an established link can be lost: a late event from a closed earlier link must not end the next one,
		// which is still connecting (and not yet established) when that event can arrive.
		BtleplugConnection connection = this.connections.get(deviceId);
		if (connection != null && connection.isConnected() && this.connections.remove(deviceId, connection)) {
			connection.lost();
		}
	}

	@Override
	public void notification(String deviceId, String serviceUuid, String characteristicUuid, byte[] value) {
		BtleplugConnection connection = this.connections.get(deviceId);
		if (connection != null) {
			connection.notification(serviceUuid, characteristicUuid, value);
		}
	}

	@Override
	public void shutdown() {
		this.control.execute(() -> {
			try {
				if (this.ready) {
					this.ble.stopScan().get(1_000, TimeUnit.MILLISECONDS);
				}
			} catch (Exception ignored) {
				// shutting down anyway
			}
		});
		this.control.shutdown();
		try {
			// Bounded, so a platform stack that hangs cannot hold up the game's exit.
			this.control.awaitTermination(2, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
