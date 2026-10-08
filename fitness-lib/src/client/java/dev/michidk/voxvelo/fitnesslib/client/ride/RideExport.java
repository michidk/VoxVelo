package dev.michidk.voxvelo.fitnesslib.client.ride;

import dev.michidk.voxvelo.fitnesslib.client.fitness.FitnessConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.Minecraft;

/** Where a finished ride goes: a FIT file in {@code .minecraft/voxvelo/rides}, or a map item from the server. */
public final class RideExport {
	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

	private RideExport() {
	}

	public static Path ridesDirectory() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("voxvelo_fitness_lib").resolve("rides");
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

private static java.util.function.BooleanSupplier mapAvailable = () -> false;
private static java.util.function.Predicate<RideRecording> mapExporter = ride -> false;
/** Optional integration supplied by a mod that owns a server map protocol. */
public static void registerMapExporter(java.util.function.BooleanSupplier available, java.util.function.Predicate<RideRecording> exporter) {
mapAvailable = java.util.Objects.requireNonNull(available);
mapExporter = java.util.Objects.requireNonNull(exporter);
}
public static boolean canRequestMap() { return mapAvailable.getAsBoolean(); }
public static boolean requestMap(RideRecording ride) { return !ride.isEmpty() && canRequestMap() && mapExporter.test(ride); }
}
