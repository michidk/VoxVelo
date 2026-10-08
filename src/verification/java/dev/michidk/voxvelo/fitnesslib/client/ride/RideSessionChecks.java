package dev.michidk.voxvelo.fitnesslib.client.ride;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.phys.Vec3;

/** Deterministic lifecycle checks with an injected clock, without a running Minecraft client. */
public final class RideSessionChecks {
	public static void main(String[] args) {
		AtomicLong clock = new AtomicLong(1_000_000);
		RideSession session = new RideSession(clock::get);
		session.start();
		capture(session, 0, "overworld");
		clock.addAndGet(50);
		capture(session, 1, "overworld");
		require(session.sampleCount() == 1 && session.distanceM() == 1, "distance tracks ticks between samples");
		session.start();
		require(session.sampleCount() == 1, "duplicate start cannot erase an active session");
		clock.addAndGet(950);
		capture(session, 2, "overworld");
		session.interrupt(); // pause, dismount or missing world
		clock.addAndGet(10_000);
		capture(session, 100, "overworld");
		require(session.distanceM() == 2, "remount does not count movement while interrupted");
		clock.addAndGet(1_000);
		capture(session, 1_000, "overworld");
		clock.addAndGet(1_000);
		capture(session, 0, "nether");
		require(session.distanceM() == 2, "teleports and dimension changes add no distance");
		RideRecording ride = session.stop();
		require(!session.isRecording() && session.sampleCount() == 0 && ride.samples().size() == 5, "stop retains an immutable finished ride");
		for (int i = 2; i < 5; i++) require(ride.samples().get(i).resumed(), "each interruption starts a new stretch");
		session.markLastRideSaved();
		require(session.stop() == ride && session.lastRideSaved(), "duplicate stop preserves the ride and saved status");
		capture(session, 5, "nether");
		require(session.sampleCount() == 0, "stopped sessions ignore samples");
		session.start();
		require(session.lastRide() == null && !session.lastRideSaved() && session.distanceM() == 0, "new session resets previous ride and saved status");
		for (int i = 0; i < RideSession.MAX_SAMPLES; i++) {
			clock.addAndGet(1_000);
			capture(session, i, "overworld");
		}
		require(!session.isRecording() && session.lastRide().samples().size() == RideSession.MAX_SAMPLES,
			"a full day of samples automatically stops and remains available");
		require(ride.samples().size() == 5, "starting another session cannot mutate an earlier recording");
		System.out.println("Ride session lifecycle checks passed.");
	}

	private static void capture(RideSession session, double x, String dimension) {
		session.capture(new Vec3(x, 64, 0), dimension, 5, 200, 90, 140, 0);
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
