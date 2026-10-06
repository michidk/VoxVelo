package dev.michidk.voxelvelo.bike;

/** Server-side physics adjustment supplied by the Speed Boost enchantment. */
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

	public static BikePhysics.Params apply(BikePhysics.Params params, int level) {
		return params.withMaxSpeed(params.maxSpeed() * multiplier(level));
	}
}
