package dev.michidk.voxvelo.fitness.client.ride;

import dev.michidk.voxvelo.fitness.network.RideMapPayload;
import dev.michidk.voxvelo.fitnesslib.client.ride.RideRecording;
import dev.michidk.voxvelo.fitnesslib.client.ride.RideSample;
import dev.michidk.voxvelo.fitnesslib.client.ride.RideSummary;
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

	private static final java.util.Map<String, dev.michidk.voxvelo.fitness.fitness.RideTerrainSketch> sketches = new java.util.HashMap<>();
	private static long recordingStart = Long.MIN_VALUE;
	private static Object recordedLevel;

	private BicycleRideExport() {}

	/** Keeps a bounded sketch of already loaded terrain for the current recording. */
	public static void tick(Minecraft client) {
		var recorder = dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime.get().rideRecorder;
		if (recordedLevel != client.level) {
			if (client.level == null) sketches.clear();
			recordedLevel = client.level;
		}
		if (!recorder.isRecording() || client.level == null || client.player == null || client.isPaused()) return;
		if (recordingStart != recorder.startMillis()) { sketches.clear(); recordingStart = recorder.startMillis(); }
		if (client.player.getVehicle() == null || client.level.dimensionType().hasCeiling()) return;
		var level = client.level;
		sketches.computeIfAbsent(level.dimension().identifier().toString(), key -> new dev.michidk.voxvelo.fitness.fitness.RideTerrainSketch())
			.captureLoaded(level, client.player.blockPosition(), 8, 1,
				(x, z) -> level.getChunkSource().getChunk(x, z, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false));
	}

	/** Writes the same route drawing as the map item without requesting an item from the server. */
	public static void writeImage(RideRecording ride, java.nio.file.Path directory) throws java.io.IOException {
		if (ride.isEmpty()) return;
		var points = routePoints(ride.samples(), RideMapPayload.MAX_POINTS);
		var sketch = recordingStart == ride.startMillis() ? sketches.get(ride.samples().getFirst().dimension()) : null;
		var drawn = dev.michidk.voxvelo.fitness.fitness.RideMapImage.draw(points, sketch);
		try (var image = new com.mojang.blaze3d.platform.NativeImage(128, 128, false)) {
			for (int z = 0; z < 128; z++) for (int x = 0; x < 128; x++) image.setPixel(x, z, drawn.argb(x, z));
			image.writeToFile(directory.resolve("map.png"));
		}
	}


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
