package dev.michidk.voxvelo.fitnesslib.client.ftms;




/**
 * Turns what the bike feels in Minecraft into FTMS Indoor Bike Simulation Parameters for a real trainer.
 * Pure functions: the safe limits in {@link TrainerLimits} are applied here so nothing the game does can drive the
 * trainer to an extreme.
 */
public final class TrainerSimulation {
	public record Target(double gradePercent, double crr) {
		public static final Target NEUTRAL = new Target(0.0, TrainerLimits.DEFAULT_CRR);
	}

	private TrainerSimulation() {
	}

	/**
	 * @param gradient  rise over run measured by the server (0.1 = 10 %)
	 * @param rollingResistance vehicle rolling-resistance coefficient
	 * @param slopeScale vehicle slope force scaling, matching its physics
	 * @param intensity user setting: 0 turns the effect off, 1 matches the game, 2 doubles it
	 */
	public static Target target(double gradient, double rollingResistance, double slopeScale, double intensity) {
		double k = clamp(intensity, TrainerLimits.MIN_INTENSITY, TrainerLimits.MAX_INTENSITY);
		// The game scales the slope force (Minecraft hills are far steeper than real roads); the trainer
		// should feel the same scaled slope, not the raw block geometry.
		double grade = gradient * 100.0 * slopeScale * k;
		double crr = rollingResistance * k;
		return new Target(
			clamp(finite(grade), TrainerLimits.MIN_GRADE_PERCENT, TrainerLimits.MAX_GRADE_PERCENT),
			clamp(finite(crr), 0.0, TrainerLimits.MAX_CRR)
		);
	}

	/** The Set Indoor Bike Simulation Parameters command: wind speed 0, then grade, rolling and wind resistance. */
	public static byte[] encode(Target target) {
		int grade = (int) Math.round(target.gradePercent() * 100.0);
		int crr = (int) Math.round(target.crr() / TrainerLimits.CRR_RESOLUTION);
		int cw = (int) Math.round(TrainerLimits.WIND_RESISTANCE / TrainerLimits.WIND_RESOLUTION);
		return new byte[] {
			(byte) FtmsControl.OP_SET_SIMULATION,
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
