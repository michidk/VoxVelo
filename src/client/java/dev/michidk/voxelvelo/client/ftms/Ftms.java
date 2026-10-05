package dev.michidk.voxelvelo.client.ftms;

/** Bluetooth SIG UUIDs for the Fitness Machine Service (FTMS). All lower case, 128-bit form. */
public final class Ftms {
	public static final String SERVICE = uuid(0x1826);
	public static final String FEATURE = uuid(0x2acc);
	public static final String INDOOR_BIKE_DATA = uuid(0x2ad2);
	public static final String TRAINING_STATUS = uuid(0x2ad3);
	public static final String CONTROL_POINT = uuid(0x2ad9);
	public static final String MACHINE_STATUS = uuid(0x2ada);

	private Ftms() {
	}

	public static String uuid(int shortUuid) {
		return String.format("%08x-0000-1000-8000-00805f9b34fb", shortUuid);
	}
}
