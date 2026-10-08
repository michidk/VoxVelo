package dev.michidk.voxvelo.client.obc;

/**
 * An OpenBikeControl device the player can connect to.
 *
 * @param key       stable identity stored in the config, e.g. "net:aabbccddeeff", "ble:AA:BB:..." or "tcp:host:port"
 * @param name      display name
 * @param transport how it is reached
 * @param host      network host (NETWORK only)
 * @param port      network port (NETWORK only)
 */
public record ObcDevice(String key, String name, Transport transport, String host, int port) {
	public enum Transport {
		NETWORK,
		BLUETOOTH
	}

	public static ObcDevice network(String id, String name, String host, int port) {
		return new ObcDevice("net:" + id, name, Transport.NETWORK, host, port);
	}

	/** A device the player typed in by hand, bypassing mDNS. */
	public static ObcDevice manual(String host, int port) {
		return new ObcDevice("tcp:" + host + ":" + port, host + ":" + port, Transport.NETWORK, host, port);
	}

	public static ObcDevice bluetooth(String address, String name) {
		return new ObcDevice("ble:" + address, name == null || name.isBlank() ? address : name, Transport.BLUETOOTH, "", 0);
	}

	public String bluetoothAddress() {
		return this.key.substring("ble:".length());
	}
}
