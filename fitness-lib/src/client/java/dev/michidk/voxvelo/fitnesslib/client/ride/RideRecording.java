package dev.michidk.voxvelo.fitnesslib.client.ride;

import java.util.List;

/**
 * A finished ride: its samples, one per second while riding, and the wall-clock span of the session.
 *
 * @param startMillis when recording was started, UTC epoch milliseconds
 * @param endMillis   when recording was stopped
 */
public record RideRecording(long startMillis, long endMillis, List<RideSample> samples) {
	public RideRecording {
		samples = List.copyOf(samples);
	}

	public boolean isEmpty() {
		return this.samples.isEmpty();
	}

	public RideSummary summary() {
		return RideSummary.of(this);
	}
}
