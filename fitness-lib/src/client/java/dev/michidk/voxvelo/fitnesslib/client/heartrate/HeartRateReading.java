package dev.michidk.voxvelo.fitnesslib.client.heartrate;

/** Latest heart rate sensor value with the time it arrived. */
public record HeartRateReading(long receivedAtMillis, int beatsPerMinute) {
	/** Straps notify about once per second and now and then skip one, so this is a little more forgiving than power. */
	public static final long STALE_AFTER_MILLIS = 5_000;

	public static HeartRateReading from(HeartRateMeasurement measurement, long nowMillis) {
		return new HeartRateReading(nowMillis, measurement.beatsPerMinute());
	}

	public boolean isFresh(long nowMillis) {
		return nowMillis - this.receivedAtMillis <= STALE_AFTER_MILLIS;
	}
}
