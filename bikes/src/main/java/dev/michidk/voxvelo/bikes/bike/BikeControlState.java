package dev.michidk.voxvelo.bikes.bike;

/**
 * Normalized rider intentions. Every input source (keyboard, FTMS trainer, OpenBikeControl)
 * is reduced to this record on the client before being sent to the server.
 *
 * @param propulsion rider power in watts (real trainer power or keyboard virtual power)
 * @param steering   -1.0 = full left, 0.0 = straight, +1.0 = full right
 * @param brake      0.0 = released, 1.0 = full brake
 * @param gear       current gear, {@link #MIN_GEAR}..{@link #MAX_GEAR}
 */
public record BikeControlState(double propulsion, double steering, double brake, int gear) {
	public static final double MAX_POWER_WATTS = 2000.0;
	public static final int MIN_GEAR = 1;
	public static final int MAX_GEAR = 12;
	public static final int DEFAULT_GEAR = 6;

	public static final BikeControlState NEUTRAL = new BikeControlState(0.0, 0.0, 0.0, DEFAULT_GEAR);

	/** Returns a copy with every field forced into its safe range; never trust values from the network. */
	public BikeControlState sanitized() {
		return new BikeControlState(
			clamp(propulsion, 0.0, MAX_POWER_WATTS),
			clamp(steering, -1.0, 1.0),
			clamp(brake, 0.0, 1.0),
			Math.max(MIN_GEAR, Math.min(MAX_GEAR, gear))
		);
	}

	private static double clamp(double value, double min, double max) {
		if (!Double.isFinite(value)) {
			return 0.0;
		}
		return Math.max(min, Math.min(max, value));
	}
}
