package dev.michidk.voxelvelo.client.ftms;

import dev.michidk.voxelvelo.bike.BikePhysics;
import dev.michidk.voxelvelo.bike.BikeSurface;

/**
 * Turns what the bike feels in Minecraft into FTMS Indoor Bike Simulation Parameters for a real trainer.
 * Pure functions: the safe limits live here so nothing the game does can drive the trainer to an extreme.
 */
public final class TrainerSimulation {
	/** Steepest downhill and uphill ever sent, in percent. Trainers refuse or misbehave far beyond this. */
	public static final double MIN_GRADE_PERCENT = -10.0;
	public static final double MAX_GRADE_PERCENT = 15.0;
	/** The FTMS rolling resistance field is a uint8 in units of 0.0001. */
	public static final double MAX_CRR = 0.0255;
	/** Wind resistance coefficient in kg/m; a typical road cyclist value. */
	public static final double WIND_RESISTANCE = 0.51;

	public static final double MIN_INTENSITY = 0.0;
	public static final double MAX_INTENSITY = 2.0;

	public record Target(double gradePercent, double crr) {
		public static final Target NEUTRAL = new Target(0.0, 0.004);
	}

	private TrainerSimulation() {
	}

	/**
	 * @param gradient  rise over run measured by the server (0.1 = 10 %)
	 * @param surface   terrain under the bike
	 * @param params    physics of the bike type, so the trainer feels what the game physics applies
	 * @param intensity user setting: 0 turns the effect off, 1 matches the game, 2 doubles it
	 */
	public static Target target(double gradient, BikeSurface surface, BikePhysics.Params params, double intensity) {
		double k = clamp(intensity, MIN_INTENSITY, MAX_INTENSITY);
		// The game scales the slope force (Minecraft hills are far steeper than real roads); the trainer
		// should feel the same scaled slope, not the raw block geometry.
		double grade = gradient * 100.0 * params.slopeGravityFactor() * k;
		double crr = params.rolling(surface) * k;
		return new Target(
			clamp(finite(grade), MIN_GRADE_PERCENT, MAX_GRADE_PERCENT),
			clamp(finite(crr), 0.0, MAX_CRR)
		);
	}

	/** FTMS Control Point op code 0x11: Set Indoor Bike Simulation Parameters (no wind). */
	public static byte[] encode(Target target) {
		int grade = (int) Math.round(target.gradePercent() * 100.0);
		int crr = (int) Math.round(target.crr() / 0.0001);
		int cw = (int) Math.round(WIND_RESISTANCE / 0.01);
		return new byte[] {
			0x11,
			0x00, 0x00,
			(byte) (grade & 0xFF), (byte) ((grade >> 8) & 0xFF),
			(byte) Math.max(0, Math.min(255, crr)),
			(byte) Math.max(0, Math.min(255, cw))
		};
	}

	private static double finite(double value) {
		return Double.isFinite(value) ? value : 0.0;
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
