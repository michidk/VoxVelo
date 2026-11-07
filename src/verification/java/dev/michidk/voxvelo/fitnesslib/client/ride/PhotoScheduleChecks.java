package dev.michidk.voxvelo.fitnesslib.client.ride;

/** Verifies active-time photo spacing across pauses, idle periods and disabled capture. */
public final class PhotoScheduleChecks {
	public static void main(String[] args) {
		var gson = new com.google.gson.Gson();
		var defaults = gson.fromJson("{}", dev.michidk.voxvelo.fitnesslib.client.fitness.FitnessConfig.class);
		require(defaults.ridePhotosEnabled && defaults.ridePhotoIntervalSeconds == 300
			&& defaults.ridePhotoMinWatts == 50 && defaults.ridePhotoMinSpeedKmh == 3
			&& defaults.ridePhotoPerspective == 0, "older settings receive safe photo defaults");
		defaults.ridePhotosEnabled = false;
		defaults.ridePhotoPerspective = 3;
		var saved = gson.fromJson(gson.toJson(defaults), defaults.getClass());
		require(!saved.ridePhotosEnabled && saved.ridePhotoPerspective == 3, "photo choices survive saving");
		PhotoSchedule schedule = new PhotoSchedule();
		for (int i = 0; i < 300; i++) require(!schedule.update(i * 1000L, true, false, 30), "idle cannot trigger");
		for (int i = 300; i < 330; i++) require(!schedule.update(i * 1000L, true, true, 30), "wait for active interval");
		require(schedule.update(330_000, true, true, 30), "capture after active interval");
		require(!schedule.update(900_000, true, false, 30), "pause cannot trigger");
		require(!schedule.update(901_000, true, true, 30), "no catch-up after pause");
		for (int i = 902; i < 920; i++) schedule.update(i * 1000L, true, true, 30);
		schedule.update(920_000, false, true, 30);
		for (int i = 921; i < 950; i++) require(!schedule.update(i * 1000L, true, true, 30), "disabled resets interval");
		require(schedule.update(950_000, true, true, 30), "fresh interval after enabling");
		schedule.reset();
		require(!schedule.update(2_000_000, true, true, 30), "new world resets clock");
		require(!schedule.update(1_000_000, true, true, 30), "backward clock cannot trigger");
		System.out.println("Ride photo active-time scheduling checks passed.");
	}
	private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
