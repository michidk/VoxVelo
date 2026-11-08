package dev.michidk.voxvelo.fitnesslib.client.ride;

import com.google.gson.JsonObject;
import dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime;
import java.nio.file.Files;
import java.time.Instant;
import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.util.Util;

/** Captures a rendered frame and restores the camera and HUD immediately afterward. */
public final class RidePhotos {
	private static final java.util.concurrent.atomic.AtomicBoolean WRITING = new java.util.concurrent.atomic.AtomicBoolean();
	private static final PhotoSchedule SCHEDULE = new PhotoSchedule();
	private static final java.time.format.DateTimeFormatter PHOTO_TIME = java.time.format.DateTimeFormatter.ofPattern("HH-mm-ss");
	private static CameraType previousCamera;
	private static CameraType photoCamera;
	private static boolean previousHud;
	private static boolean prepared;
private static boolean settingsRestored;
private static boolean requested;
public static void request() { requested = true; }
	private static int sequence;
	private static Object level;
	private RidePhotos() {}

	/** Selects the camera before extracting the frame; alternate views appear for one frame. */
	public static void prepare() {
		Minecraft client = Minecraft.getInstance();
		if (prepared) restore(client);
		FitnessRuntime runtime = FitnessRuntime.get();
		var config = runtime.config;
		var telemetry = runtime.telemetry();
		if (level != client.level) { SCHEDULE.reset(); level = client.level; }
		boolean eligible = client.level != null && client.player != null && client.player.getVehicle() != null
			&& !client.isPaused() && client.gui.screen() == null && client.isWindowActive()
			&& telemetry != null && telemetry.speedMps() * 3.6 >= config.ridePhotoMinSpeedKmh
			&& runtime.controls().powerWatts() != null && runtime.controls().powerWatts() >= config.ridePhotoMinWatts;
		boolean manual = requested && client.level != null && client.player != null && client.gui.screen() == null;
requested = false;
if (!SCHEDULE.update(System.nanoTime() / 1_000_000,
			config.ridePhotosEnabled && runtime.rideRecorder.isRecording(), eligible, config.ridePhotoIntervalSeconds) && !manual) return;
		previousCamera = client.options.getCameraType();
		previousHud = client.gui.hud.isHidden();
		sequence++;
photoCamera = switch (config.ridePhotoPerspective) {
			case 1 -> CameraType.THIRD_PERSON_BACK;
			case 2 -> CameraType.THIRD_PERSON_FRONT;
			case 4 -> CameraType.THIRD_PERSON_BACK;
case 3 -> (sequence % 2 == 0 ? CameraType.THIRD_PERSON_BACK : CameraType.THIRD_PERSON_FRONT);
			default -> previousCamera;
		};
		client.options.setCameraType(photoCamera);
		if (config.ridePhotoHideHud && !previousHud) client.gui.hud.toggle();
		settingsRestored = false;
prepared = true;
	}

	/** Reads back the frame; PNG encoding and disk writes run on the IO pool. */
	public static void capture() {
		if (!prepared) return;
		if (!WRITING.compareAndSet(false, true)) { restore(Minecraft.getInstance()); return; }
		Minecraft client = Minecraft.getInstance();
		try {
			Instant timestamp = Instant.now();
			var telemetry = FitnessRuntime.get().telemetry();
			Double measuredWatts = FitnessRuntime.get().controls().powerWatts();
			String perspective = photoCamera.name();
			String dimension = client.level.dimension().identifier().toString();
			// A photo taken during a recording goes into that ride's folder; one taken outside a ride into a shared one.
			var recorder = FitnessRuntime.get().rideRecorder;
			var directory = recorder.isRecording() ? RideExport.rideDirectory(recorder.startMillis())
				: RideExport.ridesDirectory().resolve("photos");
			String name = "photo_" + PHOTO_TIME.format(timestamp.atZone(java.time.ZoneId.systemDefault())) + "_"
				+ UUID.randomUUID().toString().substring(0, 8);
			Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), image -> Util.ioPool().execute(() -> {
				try (image) {
					Files.createDirectories(directory);
					image.writeToFile(directory.resolve(name + ".png"));
					var metadata = new JsonObject();
					metadata.addProperty("timestamp", timestamp.toString());
					metadata.addProperty("perspective", perspective);
					metadata.addProperty("dimension", dimension);
					metadata.addProperty("measuredWatts", measuredWatts);
					if (telemetry != null) {
						metadata.addProperty("watts", telemetry.powerWatts());
						metadata.addProperty("speedKmh", telemetry.speedMps() * 3.6);
						metadata.addProperty("x", telemetry.position().x);
						metadata.addProperty("y", telemetry.position().y);
						metadata.addProperty("z", telemetry.position().z);
					}
					Files.writeString(directory.resolve(name + ".json"), metadata.toString());
				} catch (Exception failure) { FitnessRuntime.LOGGER.warn("Could not save ride photo", failure); }
				finally { WRITING.set(false); }
			}));
		} catch (Exception failure) { WRITING.set(false); FitnessRuntime.LOGGER.warn("Could not capture ride photo", failure); }
		finally {
			restore(client);
		}
	}

	/** Also recovers the settings if a prepared frame was skipped instead of rendered. */
	public static void restoreExtractedView() {
if (!prepared || settingsRestored) return;
Minecraft client = Minecraft.getInstance();
client.options.setCameraType(previousCamera);
if (client.gui.hud.isHidden() != previousHud) client.gui.hud.toggle();
settingsRestored = true;
}
public static boolean detachedPhoto() { return prepared && !settingsRestored && FitnessRuntime.get().config.ridePhotoPerspective == 4; }
public static int shotSequence() { return sequence; }
private static void restore(Minecraft client) {
		restoreExtractedView();
		prepared = false;
	}
}
