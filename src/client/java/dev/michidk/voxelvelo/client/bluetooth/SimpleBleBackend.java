package dev.michidk.voxelvelo.client.bluetooth;

import dev.michidk.voxelvelo.VoxelVelo;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.simplejavable.Adapter;
import org.simplejavable.Peripheral;
import org.simplejavable.Service;

/**
 * {@link BluetoothBackend} on top of SimpleBLE (WinRT on Windows, BlueZ on Linux, CoreBluetooth on macOS).
 * This is the only class that touches the library, and it is only ever loaded on the client.
 *
 * <p>Loading the native library can fail (unsupported platform, no adapter, missing BlueZ); every such
 * failure is turned into {@link #unavailableReason()} instead of an exception.
 */
public final class SimpleBleBackend implements BluetoothBackend {
	/** How long to wait before looking for an adapter again after finding none (the user may switch Bluetooth on). */
	private static final long INIT_RETRY_MILLIS = 5_000;

	private final ExecutorService control = Executors.newSingleThreadExecutor(daemon("voxelvelo-ble-control"));
	private final ExecutorService blocking = Executors.newCachedThreadPool(daemon("voxelvelo-ble-io"));
	private final Map<String, Peripheral> peripherals = new ConcurrentHashMap<>();
	private final AtomicBoolean initStarted = new AtomicBoolean();

	private volatile Adapter adapter;
	private volatile String unavailableReason = "Bluetooth is starting up";
	private volatile boolean scanning;
	private volatile long nextInitAt;

	private static ThreadFactory daemon(String name) {
		return runnable -> {
			Thread thread = new Thread(runnable, name);
			thread.setDaemon(true);
			return thread;
		};
	}

	@Override
	public boolean isAvailable() {
		long now = System.currentTimeMillis();
		if (this.adapter == null && now >= this.nextInitAt && this.initStarted.compareAndSet(false, true)) {
			this.nextInitAt = now + INIT_RETRY_MILLIS;
			this.control.execute(this::initialise);
		}
		return this.adapter != null;
	}

	@Override
	public String unavailableReason() {
		return this.unavailableReason;
	}

	/** Records a temporary lack of adapter and logs it once per distinct reason. */
	private void noAdapter(String reason) {
		if (!reason.equals(this.unavailableReason)) {
			VoxelVelo.LOGGER.info("Bluetooth: {}", reason);
		}
		this.unavailableReason = reason;
	}

	/** Runs on the control thread. Leaves initStarted set only after a permanent failure. */
	private void initialise() {
		try {
			if (!Adapter.isBluetoothEnabled()) {
				this.noAdapter("No enabled Bluetooth adapter found");
				this.initStarted.set(false);
				return;
			}
			List<Adapter> adapters = Adapter.getAdapters();
			if (adapters.isEmpty()) {
				this.noAdapter("No Bluetooth adapter found");
				this.initStarted.set(false);
				return;
			}
			this.adapter = adapters.get(0);
			VoxelVelo.LOGGER.info("Bluetooth adapter ready: {}", this.adapter.getIdentifier());
			this.unavailableReason = "";
		} catch (Throwable t) {
			VoxelVelo.LOGGER.warn("Bluetooth is unavailable", t);
			this.unavailableReason = "Bluetooth library failed to load: " + t.getClass().getSimpleName();
		}
	}

	@Override
	public void startScan(Consumer<BluetoothDevice> onDevice) {
		this.control.execute(() -> {
			if (this.adapter == null) {
				this.initialise();
			}
			Adapter current = this.adapter;
			if (current == null) {
				return;
			}
			try {
				current.setEventListener(new ScanListener(onDevice));
				current.scanStart();
				this.scanning = true;
			} catch (Throwable t) {
				VoxelVelo.LOGGER.warn("Could not start Bluetooth scan", t);
				this.scanning = false;
			}
		});
	}

	private final class ScanListener implements Adapter.EventListener {
		private final Consumer<BluetoothDevice> onDevice;

		ScanListener(Consumer<BluetoothDevice> onDevice) {
			this.onDevice = onDevice;
		}

		@Override
		public void onScanFound(Peripheral peripheral) {
			this.report(peripheral);
		}

		@Override
		public void onScanUpdated(Peripheral peripheral) {
			this.report(peripheral);
		}

		@Override
		public void onScanStart() {
			SimpleBleBackend.this.scanning = true;
		}

		@Override
		public void onScanStop() {
			SimpleBleBackend.this.scanning = false;
		}

		private void report(Peripheral peripheral) {
			try {
				String id = peripheral.getAddress().toString();
				SimpleBleBackend.this.peripherals.put(id, peripheral);
				this.onDevice.accept(new BluetoothDevice(id, peripheral.getIdentifier(), peripheral.getRssi(), advertisedServices(peripheral)));
			} catch (Throwable t) {
				VoxelVelo.LOGGER.debug("Ignoring unreadable scan result", t);
			}
		}
	}

	private static Set<String> advertisedServices(Peripheral peripheral) {
		Set<String> uuids = new HashSet<>();
		try {
			for (Service service : peripheral.services()) {
				uuids.add(service.uuid().toLowerCase(Locale.ROOT));
			}
		} catch (Throwable ignored) {
			// Some platforms only know the services after connecting.
		}
		return uuids;
	}

	@Override
	public void stopScan() {
		this.control.execute(() -> {
			Adapter current = this.adapter;
			if (current != null) {
				try {
					current.scanStop();
				} catch (Throwable t) {
					VoxelVelo.LOGGER.debug("Stopping scan failed", t);
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
		return CompletableFuture.supplyAsync(() -> {
			Peripheral peripheral = this.peripherals.get(deviceId);
			if (peripheral == null) {
				throw new IllegalStateException("Device " + deviceId + " has not been seen by a scan");
			}
			AtomicBoolean notified = new AtomicBoolean();
			peripheral.setEventListener(new Peripheral.EventListener() {
				@Override
				public void onDisconnected() {
					if (notified.compareAndSet(false, true)) {
						onDisconnected.run();
					}
				}
			});
			peripheral.connect();
			return new SimpleBleConnection(deviceId, peripheral);
		}, this.blocking);
	}

	@Override
	public void shutdown() {
		this.control.execute(() -> {
			try {
				if (this.adapter != null) {
					this.adapter.scanStop();
				}
			} catch (Throwable ignored) {
				// shutting down anyway
			}
		});
		this.control.shutdown();
		this.blocking.shutdown();
	}
}
