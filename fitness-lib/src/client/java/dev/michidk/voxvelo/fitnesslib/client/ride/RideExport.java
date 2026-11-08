package dev.michidk.voxvelo.fitnesslib.client.ride;

import dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime;
import dev.michidk.voxvelo.fitnesslib.client.fitness.FitnessConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;

/**
 * Where a finished ride goes: its own folder in {@code .minecraft/voxvelo_fitness_lib/rides}, named after the start
 * time, holding the FIT file, the photos taken during the ride and whatever registered writers add (such as a map
 * image); or a map item from the server.
 */
public final class RideExport {
	private static final DateTimeFormatter FOLDER_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
	/** The FIT file's name inside a ride folder. */
	public static final String ACTIVITY_FILE = "activity.fit";

	/** Adds a file of its own to a ride's folder when the ride is saved. */
	@FunctionalInterface
	public interface FolderWriter {
		void write(RideRecording ride, Path directory) throws IOException;
	}

	private static final List<FolderWriter> FOLDER_WRITERS = new CopyOnWriteArrayList<>();

	private RideExport() {
	}

	public static Path ridesDirectory() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("voxvelo_fitness_lib").resolve("rides");
	}

	/** The folder of the ride that started at {@code startMillis}, named after that moment in local time. */
	public static Path rideDirectory(long startMillis) {
		return ridesDirectory().resolve(folderName(startMillis, ZoneId.systemDefault()));
	}

	/** The folder name for a ride start, such as {@code 2026-11-08_16-20-03}. */
	public static String folderName(long startMillis, ZoneId zone) {
		return FOLDER_TIME.format(Instant.ofEpochMilli(startMillis).atZone(zone));
	}

	/** Registers a writer that adds a file to every saved ride's folder. A failing writer does not stop the save. */
	public static void registerFolderWriter(FolderWriter writer) {
		FOLDER_WRITERS.add(Objects.requireNonNull(writer));
	}

	/**
	 * Writes the ride into its folder: the FIT file as {@value #ACTIVITY_FILE}, then the files of the registered
	 * writers. Saving the same ride again replaces them. Returns the FIT file's path.
	 */
	public static Path writeFit(RideRecording ride, FitnessConfig config) throws IOException {
		ZonedDateTime start = Instant.ofEpochMilli(ride.startMillis()).atZone(ZoneId.systemDefault());
		FitWriter.Options options = new FitWriter.Options(config.rideFitPosition, config.rideFitOriginLat, config.rideFitOriginLon,
			start.getOffset().getTotalSeconds());
		byte[] bytes = FitWriter.write(ride, options);
		Path directory = rideDirectory(ride.startMillis());
		Files.createDirectories(directory);
		Path file = directory.resolve(ACTIVITY_FILE);
		Path temp = directory.resolve(ACTIVITY_FILE + ".tmp");
		Files.write(temp, bytes);
		Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
		for (FolderWriter writer : FOLDER_WRITERS) {
			try {
				writer.write(ride, directory);
			} catch (IOException | RuntimeException e) {
				FitnessRuntime.LOGGER.warn("Could not add a file to the ride folder {}", directory, e);
			}
		}
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
