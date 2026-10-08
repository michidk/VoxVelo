package dev.michidk.voxvelo.client.bluetooth;

import dev.michidk.voxelfitness.client.FitnessRuntime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** A connected btleplug peripheral behind the library-neutral {@link BluetoothConnection} interface. */
final class BtleplugConnection implements BluetoothConnection {
	private final BtleplugNative ble;
	private final String deviceId;
	private final Runnable onDisconnected;
	/** The backend's open connections, which this leaves when disconnected on purpose. */
	private final Map<String, BtleplugConnection> registry;
	private final AtomicBoolean lost = new AtomicBoolean();
	/** Notification handlers by "service/characteristic". */
	private final Map<String, Consumer<byte[]>> handlers = new ConcurrentHashMap<>();
	private volatile Map<String, Map<String, Integer>> services = Map.of();
	private volatile boolean connected;

	BtleplugConnection(BtleplugNative ble, String deviceId, Runnable onDisconnected, Map<String, BtleplugConnection> registry) {
		this.ble = ble;
		this.deviceId = deviceId;
		this.onDisconnected = onDisconnected;
		this.registry = registry;
	}

	void setServices(Map<String, Map<String, Integer>> services) {
		this.services = services;
		this.connected = !this.lost.get();
	}

	/** The link dropped. Reported once. */
	void lost() {
		this.connected = false;
		if (this.lost.compareAndSet(false, true)) {
			this.onDisconnected.run();
		}
	}

	void notification(String serviceUuid, String characteristicUuid, byte[] value) {
		Consumer<byte[]> handler = this.handlers.get(key(serviceUuid, characteristicUuid));
		if (handler != null) {
			try {
				handler.accept(value);
			} catch (Throwable t) {
				FitnessRuntime.LOGGER.warn("Bluetooth notification handler failed", t);
			}
		}
	}

	private static String key(String serviceUuid, String characteristicUuid) {
		return serviceUuid + "/" + characteristicUuid;
	}

	@Override
	public String deviceId() {
		return this.deviceId;
	}

	@Override
	public boolean isConnected() {
		return this.connected;
	}

	@Override
	public Set<String> serviceUuids() {
		return Set.copyOf(this.services.keySet());
	}

	@Override
	public CompletableFuture<byte[]> read(String serviceUuid, String characteristicUuid) {
		return this.ble.read(this.deviceId, serviceUuid, characteristicUuid);
	}

	@Override
	public CompletableFuture<Void> write(String serviceUuid, String characteristicUuid, byte[] data, boolean withResponse) {
		return this.ble.write(this.deviceId, serviceUuid, characteristicUuid, data, withResponse);
	}

	/**
	 * btleplug enables notifications or indications as the characteristic supports; where both are offered it prefers
	 * indications on Windows and notifications elsewhere, which makes no difference to the data received.
	 */
	@Override
	public CompletableFuture<Void> subscribe(String serviceUuid, String characteristicUuid, Consumer<byte[]> handler) {
		this.handlers.put(key(serviceUuid, characteristicUuid), handler);
		return this.ble.subscribe(this.deviceId, serviceUuid, characteristicUuid);
	}

	/** Indicate-only characteristics (such as the Fitness Machine Control Point) are subscribed to as indications. */
	@Override
	public CompletableFuture<Void> indicate(String serviceUuid, String characteristicUuid, Consumer<byte[]> handler) {
		return this.subscribe(serviceUuid, characteristicUuid, handler);
	}

	@Override
	public CompletableFuture<Void> disconnect() {
		this.connected = false;
		this.registry.remove(this.deviceId, this);
		this.handlers.clear();
		return this.ble.disconnect(this.deviceId);
	}
}
