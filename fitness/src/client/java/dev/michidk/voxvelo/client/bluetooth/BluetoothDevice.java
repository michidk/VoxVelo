package dev.michidk.voxvelo.client.bluetooth;

import java.util.HashSet;
import java.util.Set;

/**
 * A device seen while scanning.
 *
 * @param id               stable platform identifier (MAC address, or a UUID on macOS)
 * @param name             advertised name, possibly empty
 * @param rssi             signal strength in dBm
 * @param advertisedServices lower-case 128-bit service UUIDs from the advertisement, possibly empty
 */
public record BluetoothDevice(String id, String name, int rssi, Set<String> advertisedServices) {
	public String displayName() {
		return this.name == null || this.name.isBlank() ? this.id : this.name;
	}

	public boolean advertises(String serviceUuid) {
		return this.advertisedServices.contains(serviceUuid);
	}

	/** This sighting, plus every service an earlier sighting of the same device advertised. */
	public BluetoothDevice withServicesFrom(BluetoothDevice earlier) {
		if (this.advertisedServices.containsAll(earlier.advertisedServices)) {
			return this;
		}
		Set<String> all = new HashSet<>(earlier.advertisedServices);
		all.addAll(this.advertisedServices);
		return new BluetoothDevice(this.id, this.name, this.rssi, Set.copyOf(all));
	}
}
