package dev.michidk.voxelvelo.client.ftms;

/** Independent road-load model for virtual gears over standard FTMS simulation control. */
public final class VirtualShifting {
	private VirtualShifting() {}

	/** Twelve evenly spaced ratios, with gear six matching the default physical ratio (50/20). */
	public static double ratio(int gear) {
		return 2.5 * Math.pow(1.13, Math.max(1, Math.min(12, gear)) - 6);
	}

	/**
	 * Match crank work: virtual speed = physical speed * ratio, and physical force = road force * ratio.
	 * FTMS already supplies rolling and air resistance, so subtract those before solving for grade.
	 * Downhill assistance is capped at zero load: a virtual shift must not request powered acceleration.
	 */
	public static TrainerSimulation.Target target(TrainerSimulation.Target road, int gear,
		double physicalRatio, double trainerSpeedMps, double massKg, double windResistance) {
		double relative = ratio(gear) / bounded(physicalRatio, 0.5, 6.0, 2.5);
		double speed = bounded(trainerSpeedMps, 0.0, 30.0, 0.0);
		double weight = bounded(massKg, 40.0, 200.0, 85.0) * 9.81;
		double wind = bounded(windResistance, 0.0, 2.55, 0.51);
		double slope = bounded(road.gradePercent(), -10.0, 15.0, 0.0) / 100.0;
		double rolling = bounded(road.crr(), 0.0, 0.0255, 0.004);
		double virtualSpeed = speed * relative;
		double roadForce = weight * (slope + rolling) / Math.sqrt(1.0 + slope * slope)
			+ 0.5 * wind * virtualSpeed * virtualSpeed;
		double desiredForce = Math.max(0.0, roadForce) * relative;
		// Solve sin(angle) + Crr*cos(angle) = (desiredForce - aeroForce) / weight.
		double gravityAndRolling = (desiredForce - 0.5 * wind * speed * speed) / weight;
		double angle = Math.asin(bounded(gravityAndRolling / Math.sqrt(1.0 + rolling * rolling), -0.99, 0.99, 0.0))
			- Math.atan(rolling);
		double grade = bounded(Math.tan(angle) * 100.0, -10.0, 15.0, 0.0);
		return new TrainerSimulation.Target(grade, rolling);
	}

	/** Speed telemetry is preferred; cadence supplies a fallback using the fixed physical drivetrain. */
	public static double speed(Double speedKmh, Double cadenceRpm, double physicalRatio, double circumferenceM) {
		if (speedKmh != null && Double.isFinite(speedKmh) && speedKmh >= 0.0) {
			return bounded(speedKmh / 3.6, 0.0, 30.0, 0.0);
		}
		return bounded(cadenceRpm == null ? 0.0 : cadenceRpm, 0.0, 250.0, 0.0) / 60.0
			* bounded(physicalRatio, 0.5, 6.0, 2.5) * bounded(circumferenceM, 1.0, 3.0, 2.1);
	}

	private static double bounded(double value, double min, double max, double fallback) {
		return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
	}
}
