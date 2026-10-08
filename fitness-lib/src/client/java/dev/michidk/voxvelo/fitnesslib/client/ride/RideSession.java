package dev.michidk.voxvelo.fitnesslib.client.ride;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Records a ride session on the rider's client: one sample per wall-clock second while the player rides a bike.
 *
 * <p>A session runs from {@link #start} to {@link #stop}, across dismounts and remounts; time off the bike, a paused
 * game and teleports are left out (the next sample is flagged as resumed). Distance is the path the bike actually
 * travelled, measured tick by tick. The finished ride stays available as {@link #lastRide} until the next session
 * starts, so its summary can be reopened, exported or turned into a map.
 */
final class RideSession {
	/** Movement per tick beyond this is a teleport, not riding (60 blocks per tick is 4,300 km/h). */
	private static final double TELEPORT_BLOCKS = 60.0;
	/** About 24 hours of samples; a session that long stops itself. */
	public static final int MAX_SAMPLES = 24 * 3600;

	private final java.util.function.LongSupplier clock;
	private final List<RideSample> samples = new ArrayList<>();
	private boolean recording;
	private long startMillis;
	private double distance;
	private @Nullable Vec3 lastPosition;
	private @Nullable String lastDimension;
	private long lastSampleSecond = -1;
	private boolean interrupted;
	private @Nullable RideRecording lastRide;
	private boolean lastRideSaved;

	RideSession(java.util.function.LongSupplier clock) {
		this.clock = clock;
	}

	public boolean isRecording() {
		return this.recording;
	}

	public void start() {
		if (this.recording) return;
		this.samples.clear();
		this.recording = true;
		this.startMillis = this.clock.getAsLong();
		this.distance = 0;
		this.lastPosition = null;
		this.lastDimension = null;
		this.lastSampleSecond = -1;
		this.interrupted = false;
		this.lastRide = null;
		this.lastRideSaved = false;
	}

	/** Ends the session and returns the finished ride (also kept as {@link #lastRide}). */
	public RideRecording stop() {
		if (!this.recording && this.lastRide != null) return this.lastRide;
		this.recording = false;
		this.lastRide = new RideRecording(this.startMillis, this.clock.getAsLong(), this.samples);
		this.lastRideSaved = false;
		this.samples.clear();
		return this.lastRide;
	}

	public @Nullable RideRecording lastRide() {
		return this.lastRide;
	}

	/** Whether the last ride has been written to a FIT file. */
	public boolean lastRideSaved() {
		return this.lastRideSaved;
	}

	public void markLastRideSaved() {
		this.lastRideSaved = true;
	}

	/** Milliseconds since the session started, 0 when not recording. */
	public long elapsedMillis() {
		return this.recording ? this.clock.getAsLong() - this.startMillis : 0;
	}

	public double distanceM() {
		return this.distance;
	}

	public int sampleCount() {
		return this.samples.size();
	}

	public void capture(Vec3 position, String dimension, double speed, int power, int cadence, int heartRate, double gradient) {
		if (!this.recording) return;
		if (this.lastPosition != null && dimension.equals(this.lastDimension)) {
			double moved = position.distanceTo(this.lastPosition);
			if (moved > TELEPORT_BLOCKS) {
				this.interrupted = true;
			} else {
				this.distance += moved;
			}
		} else if (this.lastPosition != null) {
			this.interrupted = true;
		}
		this.lastPosition = position;
		this.lastDimension = dimension;

		long second = this.clock.getAsLong() / 1000L;
		if (second == this.lastSampleSecond) {
			return;
		}
		this.lastSampleSecond = second;
		this.samples.add(new RideSample(second * 1000L, position.x, position.y, position.z, this.distance,
			speed, power, cadence, heartRate, gradient, dimension, this.interrupted && !this.samples.isEmpty()));
		this.interrupted = false;
		if (this.samples.size() >= MAX_SAMPLES) {
			this.stop();
		}
	}

	/** Off the bike or paused: the next sample starts a new stretch, and no distance is counted across the gap. */
	public void interrupt() {
		this.interrupted = true;
		this.lastPosition = null;
	}
}
