package dev.michidk.voxvelo.fitnesslib.client.ride;

/**
 * One second of a recorded ride. Positions are block coordinates (one block is one metre).
 *
 * @param timeMillis   wall-clock time of the sample, UTC epoch milliseconds
 * @param distanceM    distance ridden since the start of the ride, in metres
 * @param speedMs      bike speed in m/s
 * @param powerWatts   watts the rider put into the bike
 * @param cadenceRpm   crank cadence, or {@link #NO_CADENCE}
 * @param heartRate    heart rate in bpm, or {@link #NO_HEART_RATE}
 * @param gradient     grade along the direction of travel as rise/run
 * @param dimension    the dimension id, e.g. {@code minecraft:overworld}
 * @param resumed      whether the ride was interrupted just before this sample (dismount, pause, teleport,
 *                     dimension change), so the time and the line between this and the previous sample do not count
 */
public record RideSample(
	long timeMillis,
	double x,
	double y,
	double z,
	double distanceM,
	double speedMs,
	int powerWatts,
	int cadenceRpm,
	int heartRate,
	double gradient,
	String dimension,
	boolean resumed
) {
	public static final int NO_CADENCE = -1;
	public static final int NO_HEART_RATE = 0;

	public boolean hasCadence() {
		return this.cadenceRpm >= 0;
	}

	public boolean hasHeartRate() {
		return this.heartRate > 0;
	}
}
