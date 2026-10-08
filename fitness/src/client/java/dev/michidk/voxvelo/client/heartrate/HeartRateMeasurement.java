package dev.michidk.voxvelo.client.heartrate;

import org.jspecify.annotations.Nullable;

/** The heart rate value of a Bluetooth Heart Rate Measurement notification. */
public record HeartRateMeasurement(int beatsPerMinute) {
	private static final int FLAG_UINT16 = 1;

	/** Returns null if the payload is missing or truncated. */
	public static @Nullable HeartRateMeasurement parse(byte @Nullable [] data) {
		// Flags are uint8 at byte 0; bit 0 says whether the value is uint8 or little-endian uint16 after it.
		if (data == null || data.length < 2) {
			return null;
		}
		if ((data[0] & FLAG_UINT16) == 0) {
			return new HeartRateMeasurement(data[1] & 0xFF);
		}
		if (data.length < 3) {
			return null;
		}
		return new HeartRateMeasurement((data[1] & 0xFF) | ((data[2] & 0xFF) << 8));
	}
}
