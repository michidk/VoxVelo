package dev.michidk.voxvelo.client.ftms;

import org.jspecify.annotations.Nullable;

/** Latest trainer values with the time they arrived. Immutable so it can be shared across threads. */
public record TrainerTelemetry(
	long receivedAtMillis,
	int powerWatts,
	@Nullable Double cadenceRpm,
	@Nullable Double speedKmh,
	@Nullable Integer resistanceLevel,
	@Nullable Integer heartRate
) {
	/** Trainers normally notify at 1 Hz or faster; silence for this long means the value is stale. */
	public static final long STALE_AFTER_MILLIS = 3_000;

	public boolean isFresh(long nowMillis) {
		return nowMillis - this.receivedAtMillis <= STALE_AFTER_MILLIS;
	}

	/** Merges new data over old; fields a notification omits keep their last known value. */
	public static TrainerTelemetry merge(@Nullable TrainerTelemetry previous, IndoorBikeData data, long nowMillis) {
		return new TrainerTelemetry(
			nowMillis,
			data.powerWatts() != null ? Math.max(0, data.powerWatts()) : previous != null ? previous.powerWatts() : 0,
			data.cadenceRpm() != null ? data.cadenceRpm() : previous != null ? previous.cadenceRpm() : null,
			data.speedKmh() != null ? data.speedKmh() : previous != null ? previous.speedKmh() : null,
			data.resistanceLevel() != null ? data.resistanceLevel() : previous != null ? previous.resistanceLevel() : null,
			data.heartRate() != null ? data.heartRate() : previous != null ? previous.heartRate() : null
		);
	}
}
