package dev.michidk.voxvelo.client.ride;

import dev.michidk.voxvelo.network.RideMapPayload;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class BicycleRideExport {
	private static final DateTimeFormatter LABEL_TIME =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private BicycleRideExport() {}

	/** Whether the server can draw ride maps (it runs the fitness build). */
	public static boolean canRequestMap() {
		return Minecraft.getInstance().getConnection() != null
				&& ClientPlayNetworking.canSend(RideMapPayload.TYPE);
	}

	/**
	 * Asks the server for a map of the ride; false if the ride has nothing to draw or the server
	 * cannot.
	 */
	public static boolean requestMap(RideRecording ride) {
		if (ride.isEmpty() || !canRequestMap()) {
			return false;
		}
		RideSummary summary = ride.summary();
		String dimension = ride.samples().getFirst().dimension();
		List<RideMapPayload.Point> points = routePoints(ride.samples(), RideMapPayload.MAX_POINTS);
		String label =
				LABEL_TIME.format(
						Instant.ofEpochMilli(ride.startMillis()).atZone(ZoneId.systemDefault()));
		ClientPlayNetworking.send(
				RideMapPayload.of(
						dimension,
						(int) Math.round(summary.distanceM()),
						(int) Math.round(summary.timerSeconds()),
						summary.avgPowerWatts() >= 0
								? summary.avgPowerWatts()
								: RideMapPayload.NO_POWER,
						(int) Math.round(summary.ascentM()),
						label,
						points));
		return true;
	}

	/**
	 * The route in the dimension the ride started in, thinned out to at most {@code max} points.
	 * The first and last point of every stretch are kept, so interruptions stay gaps. If those
	 * endpoints alone exceed the budget, only the first {@code max} endpoints are returned. Work is
	 * linear in the number of samples.
	 */
	public static List<RideMapPayload.Point> routePoints(List<RideSample> samples, int max) {
		if (samples.isEmpty() || max <= 0) {
			return List.of();
		}
		String dimension = samples.getFirst().dimension();
		List<RideMapPayload.Point> all = new ArrayList<>();
		RideSample previous = null;
		boolean skipped = false;
		for (RideSample sample : samples) {
			if (!sample.dimension().equals(dimension)) {
				skipped = true;
				continue;
			}
			boolean newStretch =
					previous != null && (skipped || !RideSummary.continues(previous, sample));
			all.add(
					new RideMapPayload.Point(
							(int) Math.floor(sample.x()),
							(int) Math.floor(sample.z()),
							newStretch));
			previous = sample;
			skipped = false;
		}
		if (all.size() <= max) return all;
		int endpoints = 0;
		for (int i = 0; i < all.size(); i++) {
			if (isEndpoint(all, i)) endpoints++;
		}
		int optionalCount = all.size() - endpoints;
		int optionalBudget = Math.max(0, max - endpoints);
		long accumulator = 0;
		List<RideMapPayload.Point> kept = new ArrayList<>(max);
		for (int i = 0; i < all.size() && kept.size() < max; i++) {
			if (isEndpoint(all, i)) {
				kept.add(all.get(i));
			} else {
				// Spread the remaining slots evenly among interior points without rescanning the
				// route.
				accumulator += optionalBudget;
				if (accumulator >= optionalCount) {
					kept.add(all.get(i));
					accumulator -= optionalCount;
				}
			}
		}
		return kept;
	}

	private static boolean isEndpoint(List<RideMapPayload.Point> points, int i) {
		return i == 0
				|| i == points.size() - 1
				|| points.get(i).newStretch()
				|| points.get(i + 1).newStretch();
	}
}
