package dev.michidk.voxelvelo.client.ride;

import dev.michidk.voxelvelo.bike.BikeEntity;
import dev.michidk.voxelvelo.client.fitness.FitnessContext;
import dev.michidk.voxelvelo.client.ftms.TrainerTelemetry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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
public final class RideRecorder {
	/** Movement per tick beyond this is a teleport, not riding (60 blocks per tick is 4,300 km/h). */
	private static final double TELEPORT_BLOCKS = 60.0;
	/** About 24 hours of samples; a session that long stops itself. */
	public static final int MAX_SAMPLES = 24 * 3600;

	private final FitnessContext ctx;
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

	public RideRecorder(FitnessContext ctx) {
		this.ctx = ctx;
	}

	public boolean isRecording() {
		return this.recording;
	}

	public void start() {
		this.samples.clear();
		this.recording = true;
		this.startMillis = System.currentTimeMillis();
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
		this.recording = false;
		this.lastRide = new RideRecording(this.startMillis, System.currentTimeMillis(), this.samples);
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
		return this.recording ? System.currentTimeMillis() - this.startMillis : 0;
	}

	public double distanceM() {
		return this.distance;
	}

	public int sampleCount() {
		return this.samples.size();
	}

	public void tick(Minecraft client) {
		if (!this.recording) {
			return;
		}
		LocalPlayer player = client.player;
		if (player == null || client.level == null || !(player.getVehicle() instanceof BikeEntity bike) || bike.getRider() != player) {
			this.interrupt();
			return;
		}
		if (client.isPaused()) {
			this.interrupt();
			return;
		}

		Vec3 position = bike.position();
		String dimension = client.level.dimension().identifier().toString();
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

		long second = System.currentTimeMillis() / 1000L;
		if (second == this.lastSampleSecond) {
			return;
		}
		this.lastSampleSecond = second;
		this.samples.add(this.sample(bike, position, dimension, second * 1000L));
		this.interrupted = false;
		if (this.samples.size() >= MAX_SAMPLES) {
			this.stop();
		}
	}

	private RideSample sample(BikeEntity bike, Vec3 position, String dimension, long timeMillis) {
		TrainerTelemetry telemetry = this.ctx.ftms.freshTelemetry();
		int cadence;
		if (telemetry != null && telemetry.cadenceRpm() != null) {
			cadence = (int) Math.round(Math.max(0, Math.min(254, telemetry.cadenceRpm())));
		} else {
			cadence = bike.isPedaling() ? Math.round(bike.getCadenceRevsPerSecond() * 60.0F) : 0;
		}
		int heartRate = telemetry != null && telemetry.heartRate() != null && telemetry.heartRate() >= 30 && telemetry.heartRate() <= 250
			? telemetry.heartRate() : RideSample.NO_HEART_RATE;
		double gradient = this.ctx.rideTerrain.slope().orElse(bike.getGradient());
		boolean resumed = this.interrupted && !this.samples.isEmpty();
		return new RideSample(timeMillis, position.x, position.y, position.z, this.distance, bike.getSpeed(),
			(int) Math.round(Math.max(0, bike.getAppliedPower())), cadence, heartRate, gradient, dimension, resumed);
	}

	/** Off the bike or paused: the next sample starts a new stretch, and no distance is counted across the gap. */
	private void interrupt() {
		this.interrupted = true;
		this.lastPosition = null;
	}
}
