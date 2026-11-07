package dev.michidk.voxvelo.fitnesslib.client.ftms;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Independent road-load model for virtual gears over standard FTMS simulation control. */
public final class VirtualShifting {
	private VirtualShifting() {}

	public static final int GEAR_COUNT = 12;

	/** The lowest and highest virtual ratio; the gears in between are spaced evenly on a geometric curve. */
	public enum GearRange {
		DEFAULT(0.75, 5.49),
		COMPACT(1.0, 4.0),
		WIDE(0.5, 6.5),
		ONE_BY(2.2, 4.2);

		public final double lowest;
		public final double highest;

		GearRange(double lowest, double highest) {
			this.lowest = lowest;
			this.highest = highest;
		}

		public Component label() {
			return Component.translatable("voxvelo_fitness_lib.gear_range." + this.name().toLowerCase(Locale.ROOT),
				String.format(Locale.ROOT, "%.2f", this.lowest), String.format(Locale.ROOT, "%.2f", this.highest));
		}
	}

	/** Ratio of a gear on the default range. */
	public static double ratio(int gear) {
		return ratio(gear, GearRange.DEFAULT);
	}

	/** Ratio of a gear, 1..{@link #GEAR_COUNT}, with the same step between every pair of neighbours. */
	public static double ratio(int gear, GearRange range) {
		GearRange chosen = range == null ? GearRange.DEFAULT : range;
		double position = (Math.max(1, Math.min(GEAR_COUNT, gear)) - 1) / (double) (GEAR_COUNT - 1);
		return chosen.lowest * Math.pow(chosen.highest / chosen.lowest, position);
	}

	/**
	 * Match virtual road power at the trainer's measured speed. Cadence sets virtual wheel speed independently
	 * of the real cassette gear. FTMS supplies rolling and air resistance, so subtract those before solving grade.
	 * Missing cadence or speed leaves the plain road target in effect; no drivetrain telemetry is guessed.
	 */
	public static TrainerSimulation.Target target(TrainerSimulation.Target road, int gear,
		Double cadenceRpm, Double trainerSpeedKmh, double circumferenceM, double massKg, double windResistance) {
		return targetRatio(road, ratio(gear), cadenceRpm, trainerSpeedKmh, circumferenceM, massKg, windResistance);
	}

	public static TrainerSimulation.Target targetRatio(TrainerSimulation.Target road, double virtualRatio,
		Double cadenceRpm, Double trainerSpeedKmh, double circumferenceM, double massKg, double windResistance) {
		if (!telemetryAvailable(cadenceRpm, trainerSpeedKmh)) return road;
		double speed = trainerSpeedKmh / 3.6;
		double weight = bounded(massKg, 40.0, 200.0, 85.0) * 9.81;
		double wind = bounded(windResistance, 0.0, TrainerLimits.MAX_WIND_RESISTANCE, TrainerLimits.WIND_RESISTANCE);
		double slope = grade(road.gradePercent()) / 100.0;
		double rolling = bounded(road.crr(), 0.0, TrainerLimits.MAX_CRR, TrainerLimits.DEFAULT_CRR);
		double virtualSpeed = virtualSpeed(cadenceRpm, virtualRatio, circumferenceM);
		double roadForce = weight * (slope + rolling) / Math.sqrt(1.0 + slope * slope)
			+ 0.5 * wind * virtualSpeed * virtualSpeed;
		double desiredForce = Math.max(0.0, roadForce) * virtualSpeed / speed;
		// Solve sin(angle) + Crr*cos(angle) = (desiredForce - aeroForce) / weight.
		double gravityAndRolling = (desiredForce - 0.5 * wind * speed * speed) / weight;
		double angle = Math.asin(bounded(gravityAndRolling / Math.sqrt(1.0 + rolling * rolling), -0.99, 0.99, 0.0))
			- Math.atan(rolling);
		return new TrainerSimulation.Target(grade(Math.tan(angle) * 100.0), rolling);
	}

	/** A stopped trainer cannot represent power as force; wait for positive measured speed. */
	public static boolean telemetryAvailable(Double cadenceRpm, Double trainerSpeedKmh) {
		return cadenceRpm != null && Double.isFinite(cadenceRpm) && cadenceRpm >= 0.0
			&& trainerSpeedKmh != null && Double.isFinite(trainerSpeedKmh) && trainerSpeedKmh > 0.0;
	}

	/** Virtual wheel speed in metres per second, derived only from measured crank cadence. */
	public static double virtualSpeed(double cadenceRpm, double virtualRatio, double circumferenceM) {
		return bounded(cadenceRpm, 0.0, 250.0, 0.0) / 60.0
			* bounded(virtualRatio, 0.1, 100.0, TrainerLimits.DEFAULT_GEAR_RATIO)
			* bounded(circumferenceM, TrainerLimits.MIN_WHEEL_CIRCUMFERENCE_M, TrainerLimits.MAX_WHEEL_CIRCUMFERENCE_M, TrainerLimits.DEFAULT_WHEEL_CIRCUMFERENCE_M);
	}

	private static double grade(double percent) {
		return bounded(percent, TrainerLimits.MIN_GRADE_PERCENT, TrainerLimits.MAX_GRADE_PERCENT, 0.0);
	}

	private static double bounded(double value, double min, double max, double fallback) {
		return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
	}
}
