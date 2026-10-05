package dev.michidk.voxelvelo.client.ftms;

import dev.michidk.voxelvelo.bike.BikeEntity;
import dev.michidk.voxelvelo.client.fitness.FitnessContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Feeds the terrain of the bike the local player rides back to the trainer as resistance: uphill makes it
 * harder, downhill easier, sand and grass add rolling resistance. Runs from the client tick and never
 * blocks; all limits are in {@link TrainerSimulation}.
 *
 * <p>It only acts while a bike is ridden, the trainer is the propulsion source and is connected, and the
 * setting is on. When any of that stops, the trainer is returned to a flat road.
 */
public final class TrainerFeedback {
	private static final long MIN_INTERVAL_MILLIS = 750;
	private static final long SHIFT_INTERVAL_MILLIS = 150;
	/** Per-tick smoothing so single stair steps do not make the trainer jerk. */
	private static final double SMOOTHING = 0.35;
	private static final double GRADE_DEADBAND_PERCENT = 0.15;
	private static final long NEUTRAL_WAIT_ON_EXIT_MILLIS = 400;

	private final FitnessContext ctx;
	private boolean applying;
	private boolean hasSmoothed;
	private double smoothedGrade;
	private long lastSentAt;
	private TrainerSimulation.Target lastSent = TrainerSimulation.Target.NEUTRAL;
	private int lastGear;
	private FtmsControl lastControl;

	public TrainerFeedback(FitnessContext ctx) {
		this.ctx = ctx;
	}

	public void tick(Minecraft client) {
		FtmsControl control = this.ctx.ftms.control();
		if (control != this.lastControl) {
			this.applying = false;
			this.hasSmoothed = false;
			this.lastControl = control;
		}
		BikeEntity bike = this.riddenBike(client.player);
		boolean enabled = this.ctx.config.trainerResistanceEnabled
			&& this.ctx.ftms.isConnected()
			&& control != null
			&& this.ctx.inputManager.selected(dev.michidk.voxelvelo.client.input.ControlChannel.PROPULSION).id().equals("ftms");

		if (bike == null || !enabled) {
			if (this.applying && control != null) {
				control.neutral();
			}
			this.applying = false;
			this.hasSmoothed = false;
			return;
		}

		double gradient = this.ctx.rideTerrain.slope().orElse(bike.getGradient());
		TrainerSimulation.Target raw = TrainerSimulation.target(
			gradient, bike.getSurface(), bike.params(), this.ctx.config.trainerResistanceIntensity);
		this.smoothedGrade = this.hasSmoothed ? this.smoothedGrade + (raw.gradePercent() - this.smoothedGrade) * SMOOTHING : raw.gradePercent();
		this.hasSmoothed = true;
		TrainerSimulation.Target target = new TrainerSimulation.Target(this.smoothedGrade, raw.crr());
		int gear = bike.getGear();
		if (this.ctx.config.virtualShiftingEnabled) {
			TrainerTelemetry telemetry = this.ctx.ftms.freshTelemetry();
			double speed = telemetry == null ? 0.0 : VirtualShifting.speed(telemetry.speedKmh(), telemetry.cadenceRpm(),
				this.ctx.config.trainerGearRatio, this.ctx.config.trainerWheelCircumferenceM);
			target = VirtualShifting.target(target, gear, this.ctx.config.trainerGearRatio, speed,
				bike.getBikeType().params().bikeMass() + this.ctx.core.config.riderMassKg, TrainerSimulation.WIND_RESISTANCE);
		}

		long now = System.currentTimeMillis();
		boolean shifted = this.ctx.config.virtualShiftingEnabled && gear != this.lastGear;
		boolean due = !this.applying || now - this.lastSentAt >= (shifted ? SHIFT_INTERVAL_MILLIS : MIN_INTERVAL_MILLIS);
		boolean changed = Math.abs(target.gradePercent() - this.lastSent.gradePercent()) >= GRADE_DEADBAND_PERCENT
			|| Math.abs(target.crr() - this.lastSent.crr()) >= 0.0001;
		if (due && (changed || shifted || !this.applying || now - this.lastSentAt >= 5_000)) {
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
			try {
				Thread.sleep(NEUTRAL_WAIT_ON_EXIT_MILLIS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private BikeEntity riddenBike(LocalPlayer player) {
		return player != null && player.getVehicle() instanceof BikeEntity bike && bike.getRider() == player ? bike : null;
	}
}
