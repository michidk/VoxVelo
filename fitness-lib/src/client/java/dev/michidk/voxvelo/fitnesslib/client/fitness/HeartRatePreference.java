package dev.michidk.voxvelo.fitnesslib.client.fitness;


import dev.michidk.voxvelo.fitnesslib.client.ftms.TrainerTelemetry;
import dev.michidk.voxvelo.fitnesslib.client.heartrate.HeartRateReading;
import org.jspecify.annotations.Nullable;

/**
 * Chooses the heart rate sensor's value when it has a plausible one, otherwise the heart rate the trainer relays.
 * A strap without skin contact reports 0, which is why an implausible strap value falls through to the trainer.
 */
public final class HeartRatePreference {
	private HeartRatePreference() {
	}

	public static @Nullable Integer bpm(@Nullable HeartRateReading sensor, @Nullable TrainerTelemetry trainer) {
		if (sensor != null && plausible(sensor.beatsPerMinute())) return sensor.beatsPerMinute();
		if (trainer != null && trainer.heartRate() != null && plausible(trainer.heartRate())) return trainer.heartRate();
		return null;
	}

	private static boolean plausible(int bpm) {
		return bpm >= 30 && bpm <= 250;
	}
}
