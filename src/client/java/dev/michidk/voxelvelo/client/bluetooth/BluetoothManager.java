package dev.michidk.voxelvelo.client.bluetooth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * Owns the {@link BluetoothBackend}, the list of devices seen while scanning, and time-limited scans.
 * {@link #tick()} is called from the client tick; everything else may be called from any thread.
 */
public final class BluetoothManager {
	/** A device counts as currently reachable if it was seen this recently. */
	private static final long FRESH_MILLIS = 20_000;

	private final BluetoothBackend backend;
	private final Map<String, BluetoothDevice> devices = new ConcurrentHashMap<>();
	private final Map<String, Long> lastSeen = new ConcurrentHashMap<>();
	private final List<Search> searches = new ArrayList<>();
	private volatile long scanDeadline;

	private record Search(String deviceId, long deadline, CompletableFuture<BluetoothDevice> result) {}

	public BluetoothManager(BluetoothBackend backend) {
		this.backend = backend;
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

	public BluetoothBackend backend() {
		return this.backend;
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

	private void onDevice(BluetoothDevice device) {
		this.devices.put(device.id(), device);
		this.lastSeen.put(device.id(), System.currentTimeMillis());
		synchronized (this.searches) {
			Iterator<Search> it = this.searches.iterator();
			while (it.hasNext()) {
				Search search = it.next();
				if (search.deviceId().equals(device.id())) {
					search.result().complete(device);
					it.remove();
				}
			}
		}
	}

	/** Devices from the latest scans, advertised FTMS trainers first, then strongest signal. */
	public List<BluetoothDevice> devices(String preferredServiceUuid) {
		List<BluetoothDevice> list = new ArrayList<>(this.devices.values());
		list.sort(Comparator
			.comparing((BluetoothDevice d) -> !d.advertises(preferredServiceUuid))
			.thenComparing(d -> d.name() == null || d.name().isBlank())
			.thenComparing(Comparator.comparingInt(BluetoothDevice::rssi).reversed()));
		return list;
	}

	/**
	 * Completes with the device once it has been seen. Starts a scan if needed; fails with a
	 * {@link TimeoutException} if the device does not show up in time.
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
					search.result().completeExceptionally(new TimeoutException("Device not found"));
					it.remove();
				}
			}
		}
	}

	public void shutdown() {
		this.backend.shutdown();
	}
}
