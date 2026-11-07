package dev.michidk.voxvelo.fitness.client.ride;

/** Detects bike entry without restarting a manually stopped recording on the same bike. */
public final class MountRecording {
	private Object previousBike;

	/** A mount is consumed even when disabled or already recording, so toggles do not restart a ride. */
	public boolean shouldStart(Object bike, boolean enabled, boolean recording) {
		boolean entered = bike != null && bike != this.previousBike;
		this.previousBike = bike;
		return entered && enabled && !recording;
	}

	public void reset() { this.previousBike = null; }
}
