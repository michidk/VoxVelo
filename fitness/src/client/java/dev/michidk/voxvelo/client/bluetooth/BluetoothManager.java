package dev.michidk.voxvelo.client.bluetooth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the {@link BluetoothBackend}, the list of devices seen while scanning, and time-limited scans.
 * {@link #tick()} is called from the client tick; everything else may be called from any thread.
 */
public final class BluetoothManager {
	/** A device counts as currently reachable if it was seen this recently. */
	private static final long FRESH_MILLIS = 20_000;

	private final BluetoothBackend backend;
	private final SharedConnections connections;
	private final Map<String, BluetoothDevice> devices = new ConcurrentHashMap<>();
	private final Map<String, Long> lastSeen = new ConcurrentHashMap<>();
	private final List<Search> searches = new ArrayList<>();
	private volatile long scanDeadline;

	private record Search(String deviceId, long deadline, CompletableFuture<BluetoothDevice> result) {}

	public BluetoothManager(BluetoothBackend backend) {
		this.backend = backend;
		this.connections = new SharedConnections(backend);
	}

	/**
	 * Connects to a device seen by a scan. Everything that connects to the same device (a trainer that is also a power
	 * meter and heart rate source, say) shares one connection; disconnecting only gives up the caller's share.
	 * {@code onDisconnected} fires once if the link drops later.
	 */
	public CompletableFuture<BluetoothConnection> connect(String deviceId, Runnable onDisconnected) {
		return this.connections.connect(deviceId, onDisconnected);
	}

	public boolean isAvailable() {
		return this.backend.isAvailable();
	}

	public String unavailableReason() {
		return this.backend.unavailableReason();
	}

	public boolean isScanning() {
		return this.backend.isScanning();
	}

	/** Starts (or extends) a scan that stops itself after the given time. */
	public void scan(long durationMillis) {
		long deadline = System.currentTimeMillis() + durationMillis;
		this.scanDeadline = Math.max(this.scanDeadline, deadline);
		if (!this.backend.isScanning()) {
			this.backend.startScan(this::onDevice);
		}
	}

	public void stopScan() {
		this.scanDeadline = 0;
		this.backend.stopScan();
	}

	private void onDevice(BluetoothDevice seen) {
		// An advertisement and its scan response can carry different services; remember all of them so a
		// device does not drop out of a filtered list when a later packet lacks the service.
		BluetoothDevice device = this.devices.merge(seen.id(), seen, (old, now) -> now.withServicesFrom(old));
		this.lastSeen.put(device.id(), System.currentTimeMillis());
		List<CompletableFuture<BluetoothDevice>> found = new ArrayList<>();
		synchronized (this.searches) {
			Iterator<Search> it = this.searches.iterator();
			while (it.hasNext()) {
				Search search = it.next();
				if (search.deviceId().equals(device.id())) {
					found.add(search.result());
					it.remove();
				}
			}
		}
		// Callbacks may release the scan or start another search; run them after removing completed searches.
		found.forEach(result -> result.complete(device));
	}

	/** Devices from the latest scans that advertise the given service: named ones first, then strongest signal. */
	public List<BluetoothDevice> devices(String serviceUuid) {
		return this.devicesAdvertisingAny(List.of(serviceUuid));
	}

	/** Like {@link #devices(String)}, for devices that advertise at least one of the given services. */
	public List<BluetoothDevice> devicesAdvertisingAny(Collection<String> serviceUuids) {
		List<BluetoothDevice> list = new ArrayList<>();
		for (BluetoothDevice device : this.devices.values()) {
			if (serviceUuids.stream().anyMatch(device::advertises)) {
				list.add(device);
			}
		}
		list.sort(Comparator
			.comparing((BluetoothDevice d) -> d.name() == null || d.name().isBlank())
			.thenComparing(Comparator.comparingInt(BluetoothDevice::rssi).reversed()));
		return list;
	}

	/**
	 * Completes with the device once it has been seen. Starts a scan if needed; fails with a
	 * {@link DeviceNotFoundException} if the device does not show up in time.
	 */
	public CompletableFuture<BluetoothDevice> find(String deviceId, long timeoutMillis) {
		long now = System.currentTimeMillis();
		BluetoothDevice known = this.devices.get(deviceId);
		Long seen = this.lastSeen.get(deviceId);
		if (known != null && seen != null && now - seen < FRESH_MILLIS) {
			return CompletableFuture.completedFuture(known);
		}
		CompletableFuture<BluetoothDevice> result = new CompletableFuture<>();
		synchronized (this.searches) {
			this.searches.add(new Search(deviceId, now + timeoutMillis, result));
		}
		this.scan(timeoutMillis);
		return result;
	}

	/** Stops scanning unless another search is still waiting for a device. */
	public void releaseScan() {
		synchronized (this.searches) {
			if (!this.searches.isEmpty()) {
				return;
			}
		}
		this.stopScan();
	}

	/** Expires scans and searches. Call from the client tick. */
	public void tick() {
		long now = System.currentTimeMillis();
		if (this.scanDeadline != 0 && now > this.scanDeadline) {
			this.stopScan();
		}
		synchronized (this.searches) {
			Iterator<Search> it = this.searches.iterator();
			while (it.hasNext()) {
				Search search = it.next();
				if (now > search.deadline()) {
					search.result().completeExceptionally(new DeviceNotFoundException());
					it.remove();
				}
			}
		}
	}

	/** Closes every connection (waiting briefly for the platform to let go) and then stops Bluetooth. Call when the game exits. */
	public void shutdown() {
		this.stopScan();
		this.connections.closeAll(3_000);
		this.backend.shutdown();
	}
}
