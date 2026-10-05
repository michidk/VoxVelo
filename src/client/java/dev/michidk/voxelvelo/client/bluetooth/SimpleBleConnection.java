package dev.michidk.voxelvelo.client.bluetooth;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.simplejavable.BluetoothUUID;
import org.simplejavable.Characteristic;
import org.simplejavable.Peripheral;
import org.simplejavable.Service;

/** A connected SimpleBLE peripheral behind the library-neutral {@link BluetoothConnection} interface. */
final class SimpleBleConnection implements BluetoothConnection {
	private final String deviceId;
	private final Peripheral peripheral;
	private final List<Service> services;

	SimpleBleConnection(String deviceId, Peripheral peripheral) {
		this.deviceId = deviceId;
		this.peripheral = peripheral;
		this.services = peripheral.services();
	}

	@Override
	public String deviceId() {
		return this.deviceId;
	}

	@Override
	public boolean isConnected() {
		try {
			return this.peripheral.isConnected();
		} catch (Throwable t) {
			return false;
		}
	}

	@Override
	public Set<String> serviceUuids() {
		Set<String> uuids = new HashSet<>();
		for (Service service : this.services) {
			uuids.add(service.uuid().toLowerCase(Locale.ROOT));
		}
		return uuids;
	}

	@Override
	public CompletableFuture<byte[]> read(String serviceUuid, String characteristicUuid) {
		return this.peripheral.readAsync(new BluetoothUUID(serviceUuid), new BluetoothUUID(characteristicUuid));
	}

	@Override
	public CompletableFuture<Void> write(String serviceUuid, String characteristicUuid, byte[] data, boolean withResponse) {
		BluetoothUUID service = new BluetoothUUID(serviceUuid);
		BluetoothUUID characteristic = new BluetoothUUID(characteristicUuid);
		return withResponse
			? this.peripheral.writeRequestAsync(service, characteristic, data)
			: this.peripheral.writeCommandAsync(service, characteristic, data);
	}

	@Override
	public CompletableFuture<Void> subscribe(String serviceUuid, String characteristicUuid, Consumer<byte[]> handler) {
		BluetoothUUID service = new BluetoothUUID(serviceUuid);
		BluetoothUUID characteristic = new BluetoothUUID(characteristicUuid);
		Peripheral.DataCallback callback = handler::accept;
		return this.indicateOnly(serviceUuid, characteristicUuid)
			? this.peripheral.indicateAsync(service, characteristic, callback)
			: this.peripheral.notifyAsync(service, characteristic, callback);
	}

	/** Control-point style characteristics only support indications; everything else uses notifications. */
	private boolean indicateOnly(String serviceUuid, String characteristicUuid) {
		for (Service s : this.services) {
			if (!s.uuid().equalsIgnoreCase(serviceUuid)) {
				continue;
			}
			for (Characteristic c : s.characteristics()) {
				if (c.uuid().equalsIgnoreCase(characteristicUuid)) {
					return !c.canNotify() && c.canIndicate();
				}
			}
		}
		return false;
	}

	@Override
	public void disconnect() {
		this.peripheral.disconnectAsync();
	}
}
