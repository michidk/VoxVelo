package dev.michidk.voxvelo.client.ftms;

import dev.michidk.voxvelo.client.bluetooth.BleUuids;

/** Bluetooth SIG UUIDs for the Fitness Machine Service (FTMS). All lower case, 128-bit form. */
public final class Ftms {
	public static final String SERVICE = BleUuids.sig(0x1826);
	public static final String FEATURE = BleUuids.sig(0x2acc);
	public static final String INDOOR_BIKE_DATA = BleUuids.sig(0x2ad2);
	public static final String TRAINING_STATUS = BleUuids.sig(0x2ad3);
	public static final String CONTROL_POINT = BleUuids.sig(0x2ad9);
	public static final String MACHINE_STATUS = BleUuids.sig(0x2ada);

	private Ftms() {
	}
}
