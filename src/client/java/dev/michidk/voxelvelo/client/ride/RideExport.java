package dev.michidk.voxelvelo.client.ride;

import dev.michidk.voxelvelo.client.fitness.FitnessConfig;
import dev.michidk.voxelvelo.network.RideMapPayload;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** Where a finished ride goes: a FIT file in {@code .minecraft/voxelvelo/rides}, or a map item from the server. */
public final class RideExport {
	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
	private static final DateTimeFormatter LABEL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private RideExport() {
	}

	public static Path ridesDirectory() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("voxelvelo").resolve("rides");
	}

	/** Writes the ride as a FIT file and returns its path. */
	public static Path writeFit(RideRecording ride, FitnessConfig config) throws IOException {
		ZonedDateTime start = Instant.ofEpochMilli(ride.startMillis()).atZone(ZoneId.systemDefault());
		FitWriter.Options options = new FitWriter.Options(config.rideFitPosition, config.rideFitOriginLat, config.rideFitOriginLon,
			start.getOffset().getTotalSeconds());
		byte[] bytes = FitWriter.write(ride, options);
		Path directory = ridesDirectory();
		Files.createDirectories(directory);
		String base = "ride_" + FILE_TIME.format(start);
		Path file = directory.resolve(base + ".fit");
		for (int i = 2; Files.exists(file); i++) {
			file = directory.resolve(base + "_" + i + ".fit");
		}
		Path temp = directory.resolve(file.getFileName() + ".tmp");
		Files.write(temp, bytes);
		Files.move(temp, file);
		return file;
	}

	/** Whether the server can draw ride maps (it runs the fitness build). */
	public static boolean canRequestMap() {
		return Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(RideMapPayload.TYPE);
	}

	/** Asks the server for a map of the ride; false if the ride has nothing to draw or the server cannot. */
	public static boolean requestMap(RideRecording ride) {
		if (ride.isEmpty() || !canRequestMap()) {
			return false;
		}
		RideSummary summary = ride.summary();
		String dimension = ride.samples().getFirst().dimension();
		List<RideMapPayload.Point> points = routePoints(ride.samples(), RideMapPayload.MAX_POINTS);
		String label = LABEL_TIME.format(Instant.ofEpochMilli(ride.startMillis()).atZone(ZoneId.systemDefault()));
		ClientPlayNetworking.send(RideMapPayload.of(dimension, (int) Math.round(summary.distanceM()), (int) Math.round(summary.timerSeconds()),
			summary.avgPowerWatts() >= 0 ? summary.avgPowerWatts() : RideMapPayload.NO_POWER, (int) Math.round(summary.ascentM()), label, points));
		return true;
	}

	/**
	 * The route in the dimension the ride started in, thinned out to at most {@code max} points. The first and last
	 * point of every stretch are always kept, so interruptions stay gaps and the ends stay where they were.
	 */
	public static List<RideMapPayload.Point> routePoints(List<RideSample> samples, int max) {
		if (samples.isEmpty()) {
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
			boolean newStretch = previous != null && (skipped || !RideSummary.continues(previous, sample));
			all.add(new RideMapPayload.Point((int) Math.floor(sample.x()), (int) Math.floor(sample.z()), newStretch));
			previous = sample;
			skipped = false;
		}
		for (int stride = Math.max(1, all.size() / max); ; stride++) {
			List<RideMapPayload.Point> kept = new ArrayList<>();
			for (int i = 0; i < all.size(); i++) {
				RideMapPayload.Point point = all.get(i);
				boolean endOfStretch = i == all.size() - 1 || all.get(i + 1).newStretch();
				if (i % stride == 0 || point.newStretch() || endOfStretch) {
					kept.add(point);
				}
			}
			if (kept.size() <= max) {
				return kept;
			}
			if (stride > all.size()) {
				// Only stretch ends are left and there are still too many of them: keep the first ones.
				return kept.subList(0, max);
			}
		}
	}
}
