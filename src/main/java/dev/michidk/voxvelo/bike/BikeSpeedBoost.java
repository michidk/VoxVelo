package dev.michidk.voxvelo.bike;

/** Bounded movement-speed multiplier supplied by the Speed Boost enchantment. */
public final class BikeSpeedBoost {
	public static final int MAX_LEVEL = 3;
	public static final double BONUS_PER_LEVEL = 0.10;

	private BikeSpeedBoost() {
	}

	public static int clampLevel(int level) {
		return Math.max(0, Math.min(MAX_LEVEL, level));
	}

	public static double multiplier(int level) {
		return 1.0 + clampLevel(level) * BONUS_PER_LEVEL;
	}

	/** Applies the enchantment to a speed produced by the normal bike simulation. */
	public static double apply(double speed, int level) {
		return speed * multiplier(level);
	}

	/** Recovers the normal simulation speed so the multiplier is not compounded every tick. */
	public static double baseSpeed(double boostedSpeed, int level) {
		return boostedSpeed / multiplier(level);
	}
}
