package dev.michidk.voxelvelo.client.fitness;

import dev.michidk.voxelvelo.client.ftms.TrainerTelemetry;
import dev.michidk.voxelvelo.client.powermeter.PowerMeterReading;
import org.jspecify.annotations.Nullable;

/** Chooses external meter watts when available, otherwise the trainer's own estimate. */
public final class PowerPreference {
	private PowerPreference() {
	}

	public static @Nullable Integer watts(@Nullable PowerMeterReading meter, @Nullable TrainerTelemetry trainer) {
		if (meter != null) return meter.powerWatts();
		return trainer == null ? null : trainer.powerWatts();
	}
}
