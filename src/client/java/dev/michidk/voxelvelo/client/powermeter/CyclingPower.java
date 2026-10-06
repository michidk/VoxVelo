package dev.michidk.voxelvelo.client.powermeter;

import dev.michidk.voxelvelo.client.ftms.Ftms;

/** Bluetooth SIG UUIDs for the Cycling Power Service. */
public final class CyclingPower {
	public static final String SERVICE = Ftms.uuid(0x1818);
	public static final String MEASUREMENT = Ftms.uuid(0x2a63);

	private CyclingPower() {
	}
}
