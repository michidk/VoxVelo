package dev.michidk.voxelfitness.api;

/**
 * A single client tick of fresh device input. Null measurements mean unavailable, never stale
 * zeroes.
 */
public record FitnessControls(
		long tick,
		Double powerWatts,
		Double cadenceRpm,
		Integer heartRateBpm,
		double steering,
		double brake,
		int shift,
		Integer gear) {
	public static final FitnessControls NONE =
			new FitnessControls(0, null, null, null, 0, 0, 0, null);

	public FitnessControls {
		powerWatts = finite(powerWatts, 0, 3000);
		cadenceRpm = finite(cadenceRpm, 0, 254);
		heartRateBpm =
				heartRateBpm != null && heartRateBpm >= 30 && heartRateBpm <= 250
						? heartRateBpm
						: null;
		steering = bounded(steering, -1, 1);
		brake = bounded(brake, 0, 1);
		shift = Math.max(-100, Math.min(100, shift));
		gear = gear != null && gear > 0 ? gear : null;
	}

	private static Double finite(Double value, double min, double max) {
		return value != null && Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : null;
	}

	private static double bounded(double value, double min, double max) {
		return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : 0;
	}
}
