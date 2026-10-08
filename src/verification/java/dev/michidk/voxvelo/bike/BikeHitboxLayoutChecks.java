package dev.michidk.voxvelo.bike;

import net.minecraft.world.phys.Vec3;

/** Standalone checks for the rotating chain of small boxes used to target a bicycle. */
public final class BikeHitboxLayoutChecks {
	public static void main(String[] args) {
		Vec3 origin = new Vec3(10.0, 4.0, 20.0);
		Vec3 northFront = BikeHitboxLayout.position(origin, 0.0F, BikeHitboxLayout.Part.FRONT_WHEEL);
		Vec3 northRear = BikeHitboxLayout.position(origin, 0.0F, BikeHitboxLayout.Part.REAR_WHEEL);
		require(close(northFront.x, 10.0) && close(northFront.z, 20.72), "yaw zero puts the front box along positive Z");
		require(close(northRear.x, 10.0) && close(northRear.z, 19.28), "the rear box stays behind the frame");

		Vec3 eastFront = BikeHitboxLayout.position(origin, 90.0F, BikeHitboxLayout.Part.FRONT_WHEEL);
		require(close(eastFront.x, 9.28) && close(eastFront.z, 20.0), "the hitbox chain rotates with bike yaw");

		for (BikeHitboxLayout.Part part : BikeHitboxLayout.Part.values()) {
			Vec3 turned = BikeHitboxLayout.position(origin, 37.0F, part);
			double distance = Math.hypot(turned.x - origin.x, turned.z - origin.z);
			require(close(distance, Math.abs(part.forward())), "rotation preserves every part's distance from the frame");
			require(part.width() < 0.5F, "each child box remains narrow");
		}

		require(BikeHitboxLayout.Part.values().length == 5, "five boxes cover both wheels and the frame");
		System.out.println("Bike hitbox layout checks passed.");
	}

	private static boolean close(double actual, double expected) {
		return Math.abs(actual - expected) < 1.0e-9;
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
