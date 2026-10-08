package dev.michidk.voxvelo.fitnesslib.client.ride;

import dev.michidk.voxvelo.fitnesslib.api.VehicleTelemetry;
import dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime;
import dev.michidk.voxvelo.fitnesslib.client.fitness.HeartRatePreference;
import dev.michidk.voxvelo.fitnesslib.client.ftms.TrainerTelemetry;
import net.minecraft.client.Minecraft;

import org.jspecify.annotations.Nullable;

/** Adapts the game and trainer telemetry to the independently testable recording session. */
public final class RideRecorder {
	public static final int MAX_SAMPLES = RideSession.MAX_SAMPLES;
	private final FitnessRuntime ctx;
	private final RideSession session = new RideSession(System::currentTimeMillis);

	public RideRecorder(FitnessRuntime ctx) { this.ctx = ctx; }
	public boolean isRecording() { return this.session.isRecording(); }
	public void start() { this.session.start(); }
	public RideRecording stop() { return this.session.stop(); }
	public @Nullable RideRecording lastRide() { return this.session.lastRide(); }
	public boolean lastRideSaved() { return this.session.lastRideSaved(); }
	public void markLastRideSaved() { this.session.markLastRideSaved(); }
	public long elapsedMillis() { return this.session.elapsedMillis(); }
	public double distanceM() { return this.session.distanceM(); }
	public int sampleCount() { return this.session.sampleCount(); }

	public void interrupt() { this.session.interrupt(); }

public void tick(Minecraft client) {
		if (!this.session.isRecording()) return;
		VehicleTelemetry bike = this.ctx.telemetry();
		if (bike == null || client.level == null || client.isPaused()) {
			this.session.interrupt();
			return;
		}
		TrainerTelemetry telemetry = this.ctx.ftms.freshTelemetry();
		int cadence = telemetry != null && telemetry.cadenceRpm() != null
			? (int) Math.round(Math.max(0, Math.min(254, telemetry.cadenceRpm())))
			: (int) Math.round(bike.cadenceRpm());
		Integer measured = HeartRatePreference.bpm(this.ctx.heartRate.freshReading(), telemetry);
		int heartRate = measured != null ? measured : RideSample.NO_HEART_RATE;
		this.session.capture(bike.position(), client.level.dimension().identifier().toString(), bike.speedMps(),
			(int) Math.round(Math.max(0, bike.powerWatts())), cadence, heartRate,
			bike.gradient());
	}
}
