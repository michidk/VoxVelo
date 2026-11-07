package dev.michidk.voxvelo.fitness.client.ride;

/** Checks mount transitions, manual stops and the opt-in setting without a game client. */
public final class MountRecordingChecks {
	public static void main(String[] args) {
		var defaults = new com.google.gson.Gson().fromJson("{}", dev.michidk.voxvelo.fitnesslib.client.fitness.FitnessConfig.class);
		require(!defaults.rideAutoRecordOnMount, "older and new settings default off");
		MountRecording trigger = new MountRecording();
		Object bike = new Object();
		require(!trigger.shouldStart(bike, false, false), "disabled mount does not record");
		require(!trigger.shouldStart(bike, true, false), "enabling while mounted waits for entry");
		require(!trigger.shouldStart(null, true, false), "dismount does not start");
		require(trigger.shouldStart(bike, true, false), "mount starts when enabled");
		require(!trigger.shouldStart(bike, true, false), "manual stop stays stopped while mounted");
		trigger.shouldStart(null, true, true);
		require(!trigger.shouldStart(bike, true, true), "remount preserves current recording");
		require(!trigger.shouldStart(bike, true, false), "recording stop does not count as entry");
		require(trigger.shouldStart(new Object(), true, false), "switching bikes counts as entry");
		trigger.reset();
		require(trigger.shouldStart(bike, true, false), "joining resets mount tracking");
		System.out.println("Automatic bike recording transition checks passed.");
	}
	private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
