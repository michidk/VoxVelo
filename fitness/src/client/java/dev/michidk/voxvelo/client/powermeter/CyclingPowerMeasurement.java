package dev.michidk.voxvelo.client.powermeter;

import org.jspecify.annotations.Nullable;

/** The mandatory fields at the start of a Bluetooth Cycling Power Measurement notification. */
public record CyclingPowerMeasurement(int instantaneousPowerWatts) {
	/** Returns null if the payload is missing or truncated. */
	public static @Nullable CyclingPowerMeasurement parse(byte @Nullable [] data) {
		// Flags are uint16 at bytes 0-1; instantaneous power is always sint16 at bytes 2-3.
		if (data == null || data.length < 4) {
			return null;
		}
		int raw = (data[2] & 0xFF) | (data[3] << 8);
		return new CyclingPowerMeasurement((short) raw);
	}
}
