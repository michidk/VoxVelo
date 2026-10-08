package dev.michidk.voxvelo.client.heartrate;

import dev.michidk.voxvelo.client.fitness.HeartRatePreference;
import dev.michidk.voxvelo.client.ftms.TrainerTelemetry;

/** Standalone checks for Heart Rate Measurement decoding, freshness, and source precedence. */
public final class HeartRateChecks {
	public static void main(String[] args) {
		HeartRateMeasurement small = HeartRateMeasurement.parse(new byte[] {0, 72});
		require(small != null && small.beatsPerMinute() == 72, "uint8 heart rate decodes");
		HeartRateMeasurement high = HeartRateMeasurement.parse(new byte[] {0, (byte) 200});
		require(high != null && high.beatsPerMinute() == 200, "uint8 heart rate above 127 stays positive");
		HeartRateMeasurement wide = HeartRateMeasurement.parse(new byte[] {1, 0x2C, 0x01});
		require(wide != null && wide.beatsPerMinute() == 300, "uint16 heart rate decodes as little-endian");
		HeartRateMeasurement extra = HeartRateMeasurement.parse(new byte[] {0x10, 60, 0x20, 0x03});
		require(extra != null && extra.beatsPerMinute() == 60, "fields after the value do not change it");
		require(HeartRateMeasurement.parse(new byte[] {0}) == null, "short notifications are ignored");
		require(HeartRateMeasurement.parse(new byte[] {1, 0x2C}) == null, "truncated uint16 notifications are ignored");

		HeartRateReading strap = HeartRateReading.from(small, 10_000);
		require(strap.beatsPerMinute() == 72 && strap.isFresh(15_000) && !strap.isFresh(15_001), "sensor readings expire after five seconds");

		TrainerTelemetry trainer = new TrainerTelemetry(10_000, 220, 90.0, 35.0, null, 150);
		TrainerTelemetry trainerWithoutHeart = new TrainerTelemetry(10_000, 220, 90.0, 35.0, null, null);
		require(HeartRatePreference.bpm(strap, trainer) == 72, "a sensor overrides the trainer's heart rate");
		require(HeartRatePreference.bpm(null, trainer) == 150, "the trainer's heart rate is the fallback");
		require(HeartRatePreference.bpm(strap, null) == 72, "a sensor works without a trainer");
		require(HeartRatePreference.bpm(null, trainerWithoutHeart) == null, "a trainer without heart rate yields none");
		require(HeartRatePreference.bpm(null, null) == null, "no source yields no heart rate");
		HeartRateReading noContact = new HeartRateReading(10_000, 0);
		require(HeartRatePreference.bpm(noContact, trainer) == 150, "a strap without contact falls back to the trainer");
		require(HeartRatePreference.bpm(noContact, null) == null, "a strap without contact and no trainer yields none");
		require(HeartRatePreference.bpm(new HeartRateReading(10_000, 300), null) == null, "implausible values are dropped");
		require(HeartRatePreference.bpm(null, new TrainerTelemetry(10_000, 0, null, null, null, 0)) == null, "a trainer reporting 0 yields none");
		System.out.println("Heart rate checks passed.");
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
