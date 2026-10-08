package dev.michidk.voxvelo.bikes.bike;

import java.util.UUID;

/** Regression checks for server-side distance accounting across riding sessions. */
public final class BikeRideDistanceChecks {
	public static void main(String[] args) {
		BikeRideDistance distance = new BikeRideDistance();
		UUID first = new UUID(0, 1), second = new UUID(0, 2);
		require(distance.sample(first, 0, 0) == 0, "mounting establishes the starting position");
		int total = 0;
		for (int i = 1; i <= 100; i++) total += distance.sample(first, i * 0.0025, 0);
		require(total >= 24 && total <= 25, "sub-centimetre movement accumulates instead of rounding away");
		require(distance.sample(first, 100, 0) == 0, "teleports do not add distance");
		require(distance.sample(first, 101, 0) == 100, "tracking resumes from the teleport destination");
		require(distance.sample(second, 102, 0) == 0, "a new rider does not inherit the previous rider's movement");
		require(distance.sample(null, 103, 0) == 0, "an empty bike does not add distance");
		require(distance.sample(second, 104, 0) == 0, "remounting does not count the empty bike's movement");
		require(distance.sample(second, 104.03, 0.04) == 5, "distance includes both horizontal axes");
		System.out.println("Bike ride distance checks passed.");
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
