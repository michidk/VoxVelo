package dev.michidk.voxvelo.fitness.client.stats;

import dev.michidk.voxvelo.fitness.client.fitness.FitnessContext;
import dev.michidk.voxvelo.fitness.network.RiderVitalsPayload;
import dev.michidk.voxvelo.fitnesslib.client.fitness.HeartRatePreference;
import dev.michidk.voxvelo.fitnesslib.client.ftms.TrainerTelemetry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Tells the server this player's heart rate and what they share. Sent when something changes (at most once a
 * second) and as a heartbeat every five seconds, so the server can drop a pulse when the sensor goes quiet.
 */
public final class VitalsSender {
	private static final int CHECK_TICKS = 20;
	private static final int HEARTBEAT_TICKS = 100;

	private final FitnessContext ctx;
	private int ticks;
	private int sinceSent = HEARTBEAT_TICKS;
	private RiderVitalsPayload last;

	public VitalsSender(FitnessContext ctx) {
		this.ctx = ctx;
	}

	/** Forget what was sent, so the next tick sends again (a new world or server). */
	public void reset() {
		this.last = null;
		this.sinceSent = HEARTBEAT_TICKS;
		this.ticks = 0;
	}

	public void tick() {
		if (this.ticks++ % CHECK_TICKS != 0) {
			return;
		}
		this.sinceSent += CHECK_TICKS;
		TrainerTelemetry telemetry = this.ctx.ftms.freshTelemetry();
		Integer measured = HeartRatePreference.bpm(this.ctx.heartRate.freshReading(), telemetry);
		int heartRate = measured == null ? 0 : measured;
		int cadence = telemetry != null && telemetry.cadenceRpm() != null
			? (int) Math.round(Math.max(0, Math.min(300, telemetry.cadenceRpm()))) : 65535;
		RiderVitalsPayload now = RiderVitalsPayload.of(this.ctx.config.shareWatts, this.ctx.config.shareHeartRate, heartRate, cadence);
		if ((!now.equals(this.last) || this.sinceSent >= HEARTBEAT_TICKS) && ClientPlayNetworking.canSend(RiderVitalsPayload.TYPE)) {
			ClientPlayNetworking.send(now);
			this.last = now;
			this.sinceSent = 0;
		}
	}
}
