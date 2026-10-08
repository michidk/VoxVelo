package dev.michidk.voxvelo.bike;

/** Pure durability calculations shared by the entity and standalone verification. */
public final class BikeDurability {
	private static final double CRASH_THRESHOLD = 4.0;
	private static final double FALL_THRESHOLD = 3.0;

	private BikeDurability() {}

	/** Durability lost when a collision removes {@code impact} blocks/second of speed. */
	public static float crashDamage(double impact) {
		return (float) Math.max(0.0, (impact - CRASH_THRESHOLD) * 2.0);
	}

	/** Durability lost on landing after a fall of the supplied distance in blocks. */
	public static float fallDamage(double fallDistance) {
		return (float) Math.max(0.0, (fallDistance - FALL_THRESHOLD) * 3.0);
	}

	public static float clampDamage(float damage, BikeType type) {
		return Float.isFinite(damage) ? Math.clamp(damage, 0.0F, type.maxDurability) : 0.0F;
	}

	public static int conditionPercent(float damage, BikeType type) {
		return Math.round((1.0F - clampDamage(damage, type) / type.maxDurability) * 100.0F);
	}
}
