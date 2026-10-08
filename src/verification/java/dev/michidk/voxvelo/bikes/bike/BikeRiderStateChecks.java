package dev.michidk.voxvelo.bikes.bike;

/** Standalone checks for what the server accepts from a rider's report, and for the part masks. */
public final class BikeRiderStateChecks {
	public static void main(String[] args) {
		BikeRiderState wild = new BikeRiderState(99.0, -7.0, 3.0, 40, true, 1.0e6, 99, 5.0, 50.0).sanitized(12.0);
		require(wild.speed() == 12.0, "speed is capped at the bike's top speed");
		require(wild.steering() == -1.0 && wild.brake() == 1.0, "steering and brake are clamped");
		require(wild.gear() == BikeControlState.MAX_GEAR, "gear is clamped");
		require(wild.power() == BikeControlState.MAX_POWER_WATTS, "power is clamped");
		require(wild.surface() == BikeSurface.PATH.ordinal(), "an unknown surface becomes path");
		require(wild.gradient() == BikeMotion.MAX_GRADIENT, "gradient is clamped");
		require(wild.impact() == 12.0, "a crash cannot be harder than the top speed");

		BikeRiderState broken = new BikeRiderState(Double.NaN, Double.NaN, Double.POSITIVE_INFINITY, 0, false, Double.NaN, -1, Double.NaN, -3.0)
			.sanitized(12.0);
		require(broken.speed() == 0.0 && broken.steering() == 0.0 && broken.brake() == 0.0, "non-finite values become zero");
		require(broken.power() == 0.0 && broken.gradient() == 0.0 && broken.impact() == 0.0, "non-finite and negative values become zero");
		require(broken.gear() == BikeControlState.MIN_GEAR, "gear never drops below the lowest");
		require(new BikeRiderState(5.0, 0.2, 0.0, 6, true, 250.0, BikeSurface.SAND.ordinal(), 0.05, 0.0).sanitized(12.0)
			.equals(new BikeRiderState(5.0, 0.2, 0.0, 6, true, 250.0, BikeSurface.SAND.ordinal(), 0.05, 0.0)), "plausible reports pass unchanged");

		require(BikeComponents.ALL_PARTS_MASK == 31, "all five parts make up the full mask");
		require(BikeComponents.RIDEABLE_MASK == 15, "wheels, bars and saddle are needed to ride");
		require(BikeComponents.packTires(100, 37) == (100 | 37 << 8), "tire condition packs front low, rear high");
		require(BikeComponents.tirePercent(BikeComponents.packTires(100, 37), BikeComponents.REAR) == 37, "tire condition unpacks");
		require(BikeSurface.byOrdinal(-1) == BikeSurface.PATH && BikeSurface.byOrdinal(BikeSurface.ICE.ordinal()) == BikeSurface.ICE,
			"surface ordinals are looked up safely");

		double startingSpeed = 8.0;
		double coasted = BikePhysics.stepSpeed(startingSpeed, BikeMotion.DT, 0.0, 0.0, BikeSurface.PATH, 0.0, BikeType.GRAVEL.params());
		double braked = BikePhysics.stepSpeed(startingSpeed, BikeMotion.DT, 0.0, 1.0, BikeSurface.PATH, 0.0, BikeType.GRAVEL.params());
		require(coasted > 0.0 && coasted < startingSpeed, "an unattended bike keeps moving and slows under natural resistance");
		require(braked < coasted, "coasting does not apply the full brake used by an active rider");
		System.out.println("Bike rider state checks passed.");
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
