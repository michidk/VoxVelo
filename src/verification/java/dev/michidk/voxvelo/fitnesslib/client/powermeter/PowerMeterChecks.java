package dev.michidk.voxvelo.fitnesslib.client.powermeter;

import dev.michidk.voxvelo.fitnesslib.client.fitness.PowerPreference;
import dev.michidk.voxvelo.fitnesslib.client.ftms.TrainerTelemetry;

/** Standalone checks for Cycling Power decoding, freshness, and source precedence. */
public final class PowerMeterChecks {
	public static void main(String[] args) {
		CyclingPowerMeasurement normal = CyclingPowerMeasurement.parse(new byte[] {0, 0, (byte) 0xFA, 0});
		require(normal != null && normal.instantaneousPowerWatts() == 250, "instantaneous watts decode as little-endian sint16");
		CyclingPowerMeasurement negative = CyclingPowerMeasurement.parse(new byte[] {0, 0, (byte) 0x9C, (byte) 0xFF});
		require(negative != null && negative.instantaneousPowerWatts() == -100, "signed reverse power decodes correctly");
		require(CyclingPowerMeasurement.parse(new byte[] {0, 0, 1}) == null, "short notifications are ignored");

		PowerMeterReading meter = PowerMeterReading.from(normal, 10_000);
		require(meter.powerWatts() == 250 && meter.isFresh(13_000) && !meter.isFresh(13_001), "meter readings expire after three seconds");
		require(PowerMeterReading.from(negative, 10_000).powerWatts() == 0, "negative power cannot drive the bike backwards");
		TrainerTelemetry trainer = new TrainerTelemetry(10_000, 220, 90.0, 35.0, null, null);
		require(PowerPreference.watts(meter, trainer) == 250, "fresh external meter power overrides trainer power");
		require(PowerPreference.watts(null, trainer) == 220, "trainer power is the fallback");
		require(PowerPreference.watts(null, null) == null, "no fresh source yields no watts");
		System.out.println("Power meter checks passed.");
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
