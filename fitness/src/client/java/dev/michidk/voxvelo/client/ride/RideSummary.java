package dev.michidk.voxvelo.client.ride;

import java.util.List;

/**
 * The numbers of a ride, as shown at the end of a session and written to the FIT file.
 *
 * <p>Timer time counts the seconds between consecutive samples of one stretch of riding, so dismounted and paused
 * time is left out; moving time additionally leaves out standing still. Averages follow the usual conventions of
 * cycling computers: power and speed include zeros, cadence leaves out coasting, heart rate leaves out missing readings.
 *
 * @param elapsedSeconds    wall-clock time from start to stop
 * @param timerSeconds      time spent riding (recording, on the bike, game not paused)
 * @param movingSeconds     riding time while actually moving
 * @param distanceM         distance ridden in metres
 * @param avgSpeedMs        distance over moving time (0 when not moving at all)
 * @param maxSpeedMs        fastest sample
 * @param avgPowerWatts     mean power over all samples, -1 without samples
 * @param maxPowerWatts     highest power sample
 * @param normalizedPower   30-second rolling normalized power, -1 for rides shorter than 30 samples
 * @param avgCadenceRpm     mean of the non-zero cadence samples, -1 without any
 * @param maxCadenceRpm     highest cadence, -1 without any
 * @param avgHeartRate      mean of the heart-rate samples, -1 without any
 * @param maxHeartRate      highest heart rate, -1 without any
 * @param ascentM           elevation gained, with a one-block hysteresis so terrain steps do not add up noise
 * @param descentM          elevation lost, the same way
 * @param workKj            mechanical work in kilojoules
 */
public record RideSummary(
	double elapsedSeconds,
	double timerSeconds,
	double movingSeconds,
	double distanceM,
	double avgSpeedMs,
	double maxSpeedMs,
	int avgPowerWatts,
	int maxPowerWatts,
	int normalizedPower,
	int avgCadenceRpm,
	int maxCadenceRpm,
	int avgHeartRate,
	int maxHeartRate,
	double ascentM,
	double descentM,
	double workKj
) {
	/** Below this the bike counts as standing still for moving time. */
	public static final double MOVING_SPEED_MS = 0.5;
	/** Samples further apart than this belong to different stretches even if not flagged (lost ticks, lag). */
	public static final long MAX_SAMPLE_GAP_MILLIS = 2_500;
	private static final int NP_WINDOW = 30;

	/** Whether the sample continues the stretch of riding of the previous one. */
	static boolean continues(RideSample previous, RideSample sample) {
		return !sample.resumed() && sample.timeMillis() - previous.timeMillis() <= MAX_SAMPLE_GAP_MILLIS
			&& sample.dimension().equals(previous.dimension());
	}

	/**
	 * Human cycling efficiency is about 24 %, and one kcal is 4.184 kJ, so the food energy burnt is almost exactly the
	 * mechanical work in kJ. Every cycling computer uses that shortcut.
	 */
	public int calories() {
		return (int) Math.round(this.workKj);
	}

	public static RideSummary of(RideRecording ride) {
		List<RideSample> samples = ride.samples();
		double elapsed = Math.max(0, ride.endMillis() - ride.startMillis()) / 1000.0;
		if (samples.isEmpty()) {
			return new RideSummary(elapsed, 0, 0, 0, 0, 0, -1, 0, -1, -1, -1, -1, -1, 0, 0, 0);
		}

		double timer = 0;
		double moving = 0;
		double work = 0;
		double maxSpeed = 0;
		long powerSum = 0;
		int maxPower = 0;
		long cadenceSum = 0;
		int cadenceCount = 0;
		int maxCadence = -1;
		long heartSum = 0;
		int heartCount = 0;
		int maxHeart = -1;
		double ascent = 0;
		double descent = 0;
		double reference = samples.getFirst().y();

		RideSample previous = null;
		for (RideSample sample : samples) {
			if (previous != null && continues(previous, sample)) {
				double dt = (sample.timeMillis() - previous.timeMillis()) / 1000.0;
				timer += dt;
				if (sample.speedMs() >= MOVING_SPEED_MS || previous.speedMs() >= MOVING_SPEED_MS) {
					moving += dt;
				}
				work += sample.powerWatts() * dt;
			}
			if (previous == null || !continues(previous, sample)) {
				// A teleport or another dimension would be counted as a huge climb.
				reference = sample.y();
			} else if (sample.y() >= reference + 1.0) {
				ascent += sample.y() - reference;
				reference = sample.y();
			} else if (sample.y() <= reference - 1.0) {
				descent += reference - sample.y();
				reference = sample.y();
			}
			maxSpeed = Math.max(maxSpeed, sample.speedMs());
			powerSum += sample.powerWatts();
			maxPower = Math.max(maxPower, sample.powerWatts());
			if (sample.hasCadence()) {
				maxCadence = Math.max(maxCadence, sample.cadenceRpm());
				if (sample.cadenceRpm() > 0) {
					cadenceSum += sample.cadenceRpm();
					cadenceCount++;
				}
			}
			if (sample.hasHeartRate()) {
				heartSum += sample.heartRate();
				heartCount++;
				maxHeart = Math.max(maxHeart, sample.heartRate());
			}
			previous = sample;
		}

		double distance = samples.getLast().distanceM();
		return new RideSummary(
			Math.max(elapsed, timer),
			timer,
			moving,
			distance,
			moving > 0 ? distance / moving : 0,
			maxSpeed,
			(int) Math.round((double) powerSum / samples.size()),
			maxPower,
			normalizedPower(samples),
			cadenceCount > 0 ? (int) Math.round((double) cadenceSum / cadenceCount) : maxCadence >= 0 ? 0 : -1,
			maxCadence,
			heartCount > 0 ? (int) Math.round((double) heartSum / heartCount) : -1,
			maxHeart,
			ascent,
			descent,
			work / 1000.0
		);
	}

	/** Coggan's normalized power: the fourth root of the mean fourth power of the 30-second rolling average. */
	static int normalizedPower(List<RideSample> samples) {
		if (samples.size() < NP_WINDOW) {
			return -1;
		}
		double windowSum = 0;
		double fourthSum = 0;
		int count = 0;
		for (int i = 0; i < samples.size(); i++) {
			windowSum += samples.get(i).powerWatts();
			if (i >= NP_WINDOW) {
				windowSum -= samples.get(i - NP_WINDOW).powerWatts();
			}
			if (i >= NP_WINDOW - 1) {
				double average = windowSum / NP_WINDOW;
				fourthSum += average * average * average * average;
				count++;
			}
		}
		return (int) Math.round(Math.pow(fourthSum / count, 0.25));
	}
}
