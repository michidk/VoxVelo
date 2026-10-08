package dev.michidk.voxvelo.client.powermeter;

/** Latest external power-meter value with the time it arrived. */
public record PowerMeterReading(long receivedAtMillis, int powerWatts) {
	/** Power meters normally notify at least once per second. */
	public static final long STALE_AFTER_MILLIS = 3_000;

	public static PowerMeterReading from(CyclingPowerMeasurement measurement, long nowMillis) {
		return new PowerMeterReading(nowMillis, Math.max(0, measurement.instantaneousPowerWatts()));
	}

	public boolean isFresh(long nowMillis) {
		return nowMillis - this.receivedAtMillis <= STALE_AFTER_MILLIS;
	}
}
