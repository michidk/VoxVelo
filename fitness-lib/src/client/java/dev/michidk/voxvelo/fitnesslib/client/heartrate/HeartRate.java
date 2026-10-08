package dev.michidk.voxvelo.fitnesslib.client.heartrate;

import dev.michidk.voxvelo.fitnesslib.client.bluetooth.BleUuids;

/** Bluetooth SIG UUIDs for the Heart Rate Service. */
public final class HeartRate {
	public static final String SERVICE = BleUuids.sig(0x180d);
	public static final String MEASUREMENT = BleUuids.sig(0x2a37);

	private HeartRate() {
	}
}
