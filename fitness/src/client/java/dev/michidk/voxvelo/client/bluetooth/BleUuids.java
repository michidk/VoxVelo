package dev.michidk.voxvelo.client.bluetooth;

import java.util.Locale;

/** Bluetooth SIG UUIDs in the lower case, 128-bit form the backends report. */
public final class BleUuids {
	private BleUuids() {
	}

	/** The full UUID of a 16-bit number assigned by the Bluetooth SIG, such as 0x1826 for the Fitness Machine Service. */
	public static String sig(int shortUuid) {
		return String.format(Locale.ROOT, "%08x-0000-1000-8000-00805f9b34fb", shortUuid);
	}
}
