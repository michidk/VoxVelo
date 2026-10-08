package dev.michidk.voxvelo.client.ftms;

/**
 * Every limit on what is sent to a trainer and on the trainer settings, in one place so the simulation, virtual shifting,
 * the config and the settings screen cannot disagree.
 */
public final class TrainerLimits {
	/** Steepest downhill and uphill ever sent, in percent. Trainers refuse or misbehave far beyond this. */
	public static final double MIN_GRADE_PERCENT = -10.0;
	public static final double MAX_GRADE_PERCENT = 15.0;

	/** The FTMS rolling resistance field is a uint8 in units of 0.0001. */
	public static final double CRR_RESOLUTION = 0.0001;
	public static final double MAX_CRR = 0.0255;
	/** Rolling resistance of an ordinary road, sent when the game asks for nothing else. */
	public static final double DEFAULT_CRR = 0.004;

	/** The FTMS wind resistance field is a uint8 in units of 0.01 kg/m. */
	public static final double WIND_RESOLUTION = 0.01;
	public static final double MAX_WIND_RESISTANCE = 2.55;
	/** Wind resistance coefficient in kg/m; a typical road cyclist value. */
	public static final double WIND_RESISTANCE = 0.51;

	/** Resistance intensity setting: 0 turns the effect off, 1 matches the game, 2 doubles it. */
	public static final double MIN_INTENSITY = 0.0;
	public static final double MAX_INTENSITY = 2.0;
	public static final double DEFAULT_INTENSITY = 1.0;

	/** Physical gear ratio of the real bike on the trainer, chainring over cog; the default is 50/20. */
	public static final double MIN_GEAR_RATIO = 0.5;
	public static final double MAX_GEAR_RATIO = 6.0;
	public static final double DEFAULT_GEAR_RATIO = 2.5;

	/** Circumference of the trainer's wheel in metres, for speed from cadence. */
	public static final double MIN_WHEEL_CIRCUMFERENCE_M = 1.0;
	public static final double MAX_WHEEL_CIRCUMFERENCE_M = 3.0;
	public static final double DEFAULT_WHEEL_CIRCUMFERENCE_M = 2.1;

	private TrainerLimits() {
	}
}
