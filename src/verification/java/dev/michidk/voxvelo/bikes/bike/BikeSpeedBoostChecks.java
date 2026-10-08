package dev.michidk.voxvelo.bikes.bike;

/** Standalone checks for the bounded movement-speed enchantment multiplier. */
public final class BikeSpeedBoostChecks {
	public static void main(String[] args) {
		require(BikeSpeedBoost.multiplier(0) == 1.0, "an unenchanted bike keeps its movement speed");
		require(BikeSpeedBoost.multiplier(1) == 1.1, "level one adds ten percent");
		require(BikeSpeedBoost.multiplier(3) == 1.3, "level three adds thirty percent");
		require(BikeSpeedBoost.multiplier(99) == 1.3, "invalid high levels are capped");
		require(BikeSpeedBoost.multiplier(-1) == 1.0, "invalid negative levels are ignored");
		require(close(BikeSpeedBoost.apply(8.0, 2), 9.6), "the enchantment multiplies actual movement speed");
		double boosted = BikeSpeedBoost.apply(8.0, 3);
		require(close(BikeSpeedBoost.baseSpeed(boosted, 3), 8.0),
			"converting back before the next physics tick prevents exponential compounding");
		double limited = RiderSettings.capSpeed(boosted, new RiderSettings.Settings(75.0, 20.0));
		require(close(limited, 20.0 / 3.6), "the rider speed limit remains the final cap");
		System.out.println("Bike speed boost checks passed.");
	}

	private static boolean close(double actual, double expected) {
		return Math.abs(actual - expected) < 1.0e-9;
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
