package dev.michidk.voxvelo.fitnesslib.client.ftms;

/** Independent road-load model for virtual gears over standard FTMS simulation control. */
public final class VirtualShifting {
	private VirtualShifting() {}

	/** Twelve evenly spaced ratios, with gear six matching the default physical ratio (50/20). */
	public static double ratio(int gear) {
		return TrainerLimits.DEFAULT_GEAR_RATIO * Math.pow(1.13, Math.max(1, Math.min(12, gear)) - 6);
	}

	/**
	 * Match crank work: virtual speed = physical speed * ratio, and physical force = road force * ratio.
	 * FTMS already supplies rolling and air resistance, so subtract those before solving for grade.
	 * Downhill assistance is capped at zero load: a virtual shift must not request powered acceleration.
	 */
	public static TrainerSimulation.Target target(TrainerSimulation.Target road, int gear,
		double physicalRatio, double trainerSpeedMps, double massKg, double windResistance) {
		return targetRatio(road, ratio(gear), physicalRatio, trainerSpeedMps, massKg, windResistance);
}

public static TrainerSimulation.Target targetRatio(TrainerSimulation.Target road, double virtualRatio,
double physicalRatio, double trainerSpeedMps, double massKg, double windResistance) {
double relative = bounded(virtualRatio, 0.1, 100, TrainerLimits.DEFAULT_GEAR_RATIO) / gearRatio(physicalRatio);
		double speed = bounded(trainerSpeedMps, 0.0, 30.0, 0.0);
		double weight = bounded(massKg, 40.0, 200.0, 85.0) * 9.81;
		double wind = bounded(windResistance, 0.0, TrainerLimits.MAX_WIND_RESISTANCE, TrainerLimits.WIND_RESISTANCE);
		double slope = grade(road.gradePercent()) / 100.0;
		double rolling = bounded(road.crr(), 0.0, TrainerLimits.MAX_CRR, TrainerLimits.DEFAULT_CRR);
		double virtualSpeed = speed * relative;
		double roadForce = weight * (slope + rolling) / Math.sqrt(1.0 + slope * slope)
			+ 0.5 * wind * virtualSpeed * virtualSpeed;
		double desiredForce = Math.max(0.0, roadForce) * relative;
		// Solve sin(angle) + Crr*cos(angle) = (desiredForce - aeroForce) / weight.
		double gravityAndRolling = (desiredForce - 0.5 * wind * speed * speed) / weight;
		double angle = Math.asin(bounded(gravityAndRolling / Math.sqrt(1.0 + rolling * rolling), -0.99, 0.99, 0.0))
			- Math.atan(rolling);
		return new TrainerSimulation.Target(grade(Math.tan(angle) * 100.0), rolling);
	}

	/** Speed telemetry is preferred; cadence supplies a fallback using the fixed physical drivetrain. */
	public static double speed(Double speedKmh, Double cadenceRpm, double physicalRatio, double circumferenceM) {
		if (speedKmh != null && Double.isFinite(speedKmh) && speedKmh >= 0.0) {
			return bounded(speedKmh / 3.6, 0.0, 30.0, 0.0);
		}
		return bounded(cadenceRpm == null ? 0.0 : cadenceRpm, 0.0, 250.0, 0.0) / 60.0
			* gearRatio(physicalRatio)
			* bounded(circumferenceM, TrainerLimits.MIN_WHEEL_CIRCUMFERENCE_M, TrainerLimits.MAX_WHEEL_CIRCUMFERENCE_M, TrainerLimits.DEFAULT_WHEEL_CIRCUMFERENCE_M);
	}

	private static double gearRatio(double ratio) {
		return bounded(ratio, TrainerLimits.MIN_GEAR_RATIO, TrainerLimits.MAX_GEAR_RATIO, TrainerLimits.DEFAULT_GEAR_RATIO);
	}

	private static double grade(double percent) {
		return bounded(percent, TrainerLimits.MIN_GRADE_PERCENT, TrainerLimits.MAX_GRADE_PERCENT, 0.0);
	}

	private static double bounded(double value, double min, double max, double fallback) {
		return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
	}
}
