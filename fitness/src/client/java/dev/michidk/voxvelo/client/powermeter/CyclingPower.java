package dev.michidk.voxvelo.client.powermeter;

import dev.michidk.voxvelo.client.bluetooth.BleUuids;

/** Bluetooth SIG UUIDs for the Cycling Power Service. */
public final class CyclingPower {
	public static final String SERVICE = BleUuids.sig(0x1818);
	public static final String MEASUREMENT = BleUuids.sig(0x2a63);

	private CyclingPower() {
	}
}
