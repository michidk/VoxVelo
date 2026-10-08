package dev.michidk.voxvelo.fitnesslib.client.ftms;

import dev.michidk.voxvelo.fitnesslib.api.VehicleTelemetry;
import dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime;
import net.minecraft.client.Minecraft;


/**
 * Feeds the terrain of the bike the local player rides back to the trainer as resistance: uphill makes it
 * harder, downhill easier, sand and grass add rolling resistance. Runs from the client tick and never
 * blocks; all limits are in {@link TrainerSimulation}.
 *
 * <p>It only acts while a bike is ridden, the trainer is connected, and the setting is on. When any of that stops, the trainer is returned to a flat road.
 */
public final class TrainerFeedback {
	/** Least time between two updates for a small change; the in-flight limit in {@link FtmsControl} still applies. */
	private static final long MIN_INTERVAL_MILLIS = 400;
	/** Least time between two updates for a big change (a hill starting) or a gear change, so those are felt at once. */
	private static final long URGENT_INTERVAL_MILLIS = 150;
	/** A grade change of at least this much since the last update is sent without waiting out the normal interval. */
	private static final double URGENT_GRADE_STEP_PERCENT = 0.5;
	/** Per-tick smoothing so single stair steps do not make the trainer jerk. */
	private static final double SMOOTHING = 0.35;
	private static final double GRADE_DEADBAND_PERCENT = 0.1;
	/** An unchanged target is sent again this often, so a trainer that dropped a command still ends up on it. */
	private static final long REFRESH_MILLIS = 5_000;
	/** Time the neutral command gets to reach the trainer on game exit, before Bluetooth is shut down. */
	private static final long NEUTRAL_WAIT_ON_EXIT_MILLIS = 400;

	private final FitnessRuntime ctx;
	private boolean applying;
	private boolean hasSmoothed;
	private double smoothedGrade;
	private long lastSentAt;
	private TrainerSimulation.Target lastSent = TrainerSimulation.Target.NEUTRAL;
	private double lastGear;
	private FtmsControl lastControl;

	public TrainerFeedback(FitnessRuntime ctx) {
		this.ctx = ctx;
	}

	public void tick(Minecraft client) {
		FtmsControl control = this.ctx.ftms.control();
		if (control != this.lastControl) {
			this.applying = false;
			this.hasSmoothed = false;
			this.lastControl = control;
		}
		VehicleTelemetry bike = this.ctx.telemetry();
		boolean enabled = this.ctx.config.trainerResistanceEnabled
			&& this.ctx.ftms.isConnected()
			&& control != null;

		if (bike == null || !enabled) {
			if (this.applying && control != null) {
				control.neutral();
			}
			this.applying = false;
			this.hasSmoothed = false;
			return;
		}

		double gradient = bike.gradient();
		TrainerSimulation.Target raw = TrainerSimulation.target(
			gradient, bike.rollingResistance(), bike.slopeScale(), this.ctx.config.trainerResistanceIntensity);
		this.smoothedGrade = this.hasSmoothed ? this.smoothedGrade + (raw.gradePercent() - this.smoothedGrade) * SMOOTHING : raw.gradePercent();
		this.hasSmoothed = true;
		TrainerSimulation.Target target = new TrainerSimulation.Target(this.smoothedGrade, raw.crr());
		double gear = bike.virtualGearRatio();
		if (this.ctx.config.virtualShiftingEnabled && gear > 0) {
			TrainerTelemetry telemetry = this.ctx.ftms.freshTelemetry();
			double speed = telemetry == null ? 0.0 : VirtualShifting.speed(telemetry.speedKmh(), telemetry.cadenceRpm(),
				this.ctx.config.trainerGearRatio, this.ctx.config.trainerWheelCircumferenceM);
			target = VirtualShifting.targetRatio(target, gear, this.ctx.config.trainerGearRatio, speed,
				bike.massKg(), TrainerLimits.WIND_RESISTANCE);
		}

		long now = System.currentTimeMillis();
		boolean shifted = this.ctx.config.virtualShiftingEnabled && gear != this.lastGear;
		boolean urgent = shifted || Math.abs(target.gradePercent() - this.lastSent.gradePercent()) >= URGENT_GRADE_STEP_PERCENT;
		boolean due = !this.applying || now - this.lastSentAt >= (urgent ? URGENT_INTERVAL_MILLIS : MIN_INTERVAL_MILLIS);
		boolean changed = Math.abs(target.gradePercent() - this.lastSent.gradePercent()) >= GRADE_DEADBAND_PERCENT
			|| Math.abs(target.crr() - this.lastSent.crr()) >= TrainerLimits.CRR_RESOLUTION;
		if (due && (changed || shifted || !this.applying || now - this.lastSentAt >= REFRESH_MILLIS)) {
			control.apply(target);
			this.lastSent = target;
			this.lastSentAt = now;
			this.lastGear = gear;
		}
		this.applying = true;
	}

	/** Best effort on game exit: leave the trainer on a flat road instead of the last hill. */
	public void shutdown() {
		FtmsControl control = this.ctx.ftms.control();
		if (this.applying && control != null) {
			control.neutral();
			// The one place the game thread waits on Bluetooth: the game is exiting and the connection is closed right
			// after this, which would drop the command. Only while resistance is applied, and bounded.
			try {
				Thread.sleep(NEUTRAL_WAIT_ON_EXIT_MILLIS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

public void neutral() {
FtmsControl control = ctx.ftms.control();
if (applying && control != null) control.neutral();
applying = false; hasSmoothed = false;
}
}
