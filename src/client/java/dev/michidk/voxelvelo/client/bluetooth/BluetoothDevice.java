package dev.michidk.voxelvelo.client.bluetooth;

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
}
